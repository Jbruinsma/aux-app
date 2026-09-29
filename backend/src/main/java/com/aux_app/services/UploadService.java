package com.aux_app.services;

import java.awt.color.ColorSpace;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.PlaylistRepository;
import com.aux_app.repository.UserRepository;
import com.luciad.imageio.webp.CompressionType;

import jakarta.persistence.EntityManager;
import com.luciad.imageio.webp.WebPWriteParam;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// Validates user image and MP3 uploads and stores them in R2.
// Nothing the client sends is stored as-is: the image is decoded, normalized to sRGB, rotated per EXIF,
// center-cropped, and re-encoded as WebP. That strips metadata (GPS etc.) and any non-image payload
// (polyglot files, trailing data), so the bucket only ever holds pixels we produced.
// MP3s keep their audio untouched, but only the MPEG frames are stored: ID3 tags (embedded pictures,
// metadata) and anything else around the frames are dropped. They go to a private bucket and are only
// reachable through short-lived signed URLs, so tracks in private playlists don't leak.
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    static final long MAX_BYTES = 5 * 1024 * 1024;
    // Checked from the header before decoding, so a small file can't expand into gigabytes of pixels
    static final long MAX_PIXELS = 25_000_000;
    static final int MIN_SIDE = 256;
    static final int OUTPUT_SIDE = 1024;
    static final int BANNER_MIN_WIDTH = 600;
    static final int BANNER_MIN_HEIGHT = 200;
    static final int BANNER_MAX_WIDTH = 1500;
    static final int BANNER_MAX_HEIGHT = 500;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png");

    static final int MIN_TRACK_SECONDS = 5;
    static final int MAX_TRACK_SECONDS = 10 * 60;
    static final long MAX_STORAGE_BYTES = 2L * 1024 * 1024 * 1024;
    // Room for ID3v1/APE/Lyrics3 tags after the last frame. More than that means the frame chain broke
    // mid-file, and storing only the frames before the break would silently cut the song short
    static final int MAX_TRAILING_BYTES = 256 * 1024;
    // Long enough to play a 10 minute track with pauses; the frontend asks for a new URL when one expires
    private static final Duration SIGNED_URL_TTL = Duration.ofHours(1);
    // Layer III bitrates in kbps by bitrate index; 0 (free format) and 15 are invalid
    private static final int[] MPEG1_KBPS = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] MPEG2_KBPS = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};

    // TODO: caps decode memory at ~4 x 100MB (25MP ARGB); tune with heap size or move to a queue
    private static final Semaphore DECODE_SLOTS = new Semaphore(4);

    private final S3Client r2;
    // Separate token scoped to the audio bucket only
    private final S3Client audioR2;
    private final S3Presigner presigner;
    private final String bucket;
    private final String audioBucket;
    private final String publicBaseUrl;
    private final UserRepository users;
    private final MusicPieceRepository musicPieces;
    private final PlaylistRepository playlists;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    // aux.r2.* come from AUX_R2_* and AUX_PRIVATE_TRACKS_R2_* in .env (see application.properties)
    public UploadService(
            @Value("${aux.r2.account-id}") String accountId,
            @Value("${aux.r2.access-key-id}") String accessKeyId,
            @Value("${aux.r2.secret-access-key}") String secretAccessKey,
            @Value("${aux.r2.bucket}") String bucket,
            @Value("${aux.r2.audio-access-key-id}") String audioAccessKeyId,
            @Value("${aux.r2.audio-secret-access-key}") String audioSecretAccessKey,
            @Value("${aux.r2.audio-bucket}") String audioBucket,
            @Value("${aux.r2.public-base-url}") String publicBaseUrl,
            UserRepository users,
            MusicPieceRepository musicPieces,
            PlaylistRepository playlists,
            EntityManager entityManager,
            TransactionTemplate transactions
    ) {
        URI endpoint = URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        StaticCredentialsProvider audioCredentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(audioAccessKeyId, audioSecretAccessKey));
        this.r2 = r2Client(endpoint, StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        this.audioR2 = r2Client(endpoint, audioCredentials);
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(audioCredentials)
                .build();
        this.bucket = bucket;
        this.audioBucket = audioBucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.users = users;
        this.musicPieces = musicPieces;
        this.playlists = playlists;
        this.entityManager = entityManager;
        this.transactions = transactions;
    }

    private static S3Client r2Client(URI endpoint, StaticCredentialsProvider credentials) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                // R2 doesn't support every default checksum newer SDKs send
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    public String replaceProfilePicture(UserEntity user, MultipartFile file) {
        byte[] webp = toWebp(readUpload(file));
        return store("pfp", webp, user.getProfilePictureUrl(), user::setProfilePictureUrl, () -> users.save(user));
    }

    // Crop is in pixels of the EXIF-rotated original, i.e. what the browser shows the user
    public String replaceBanner(UserEntity user, MultipartFile file, int x, int y, int width, int height) {
        byte[] webp = bannerToWebp(readUpload(file), x, y, width, height);
        return store("banner", webp, user.getBannerUrl(), user::setBannerUrl, () -> users.save(user));
    }

    // Also saves the playlist, so a new playlist can be passed in unsaved: its cover column is NOT NULL.
    // Any other pending changes on the entity are saved with it
    public String replacePlaylistCover(PlaylistEntity playlist, MultipartFile file) {
        byte[] webp;
        try {
            webp = toWebp(readUpload(file));
        } catch (AuxException e) {
            // The image checks report the pfp/banner field name
            throw new AuxException(e.getStatus(), e.getDetails().code(), e.getMessage(), "playlistCover");
        }
        return store("playlist-cover", webp, playlist.getPlaylistCoverUrl(), playlist::setPlaylistCoverUrl,
                () -> playlists.save(playlist));
    }

    // mp3FileUrl in music_pieces holds this key; turn it into a playable URL with signedAudioUrl.
    public record StoredTrack(String key, int durationSeconds, int sizeBytes) {}

    record Mp3(byte[] frames, int durationSeconds) {}

    // Stores a batch of MP3s and inserts their music_pieces rows; toPieces builds one row per track, in upload
    // order. All or nothing: if any file is invalid, the quota is exceeded, or the insert fails, no row is
    // saved and no object is left in R2. Per-file size is capped by spring.servlet.multipart.
    public List<MusicPieceEntity> storeTracks(
            UserEntity user,
            List<MultipartFile> files,
            Function<List<StoredTrack>, List<MusicPieceEntity>> toPieces
    ) {
        // SQLite takes its write lock at BEGIN (transaction_mode=IMMEDIATE), so a caller's transaction would
        // block every other writer in the app for as long as the R2 uploads below take
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("storeTracks must not be called inside a transaction");
        }
        // Validate every file before uploading any, so one bad file doesn't leave the others orphaned
        List<Mp3> tracks = files.stream().map(UploadService::readMp3).toList();
        long batchBytes = tracks.stream().mapToLong(t -> t.frames().length).sum();
        // Early check so an over-quota batch fails before uploading; the check that counts is the one below
        if (musicPieces.totalSizeBytes(user.getUserId()) + batchBytes > MAX_STORAGE_BYTES) {
            throw quotaExceeded();
        }

        List<StoredTrack> stored = new ArrayList<>();
        try {
            for (Mp3 track : tracks) {
                String key = "mp3/" + UUID.randomUUID() + ".mp3";
                audioR2.putObject(put -> put.bucket(audioBucket).key(key).contentType("audio/mpeg"),
                        RequestBody.fromBytes(track.frames()));
                stored.add(new StoredTrack(key, track.durationSeconds(), track.frames().length));
            }
            // Parallel batches from one user can all pass the early check. Writers are serialized here (the
            // write lock is held from BEGIN), so each one sees every committed batch before it and the total
            // can never pass the limit
            return transactions.execute(status -> {
                List<MusicPieceEntity> pieces = toPieces.apply(List.copyOf(stored));
                // persist, not save: save merges, which would overwrite an existing row with a colliding id
                pieces.forEach(entityManager::persist);
                entityManager.flush();
                if (musicPieces.totalSizeBytes(user.getUserId()) > MAX_STORAGE_BYTES) {
                    throw quotaExceeded();
                }
                return pieces;
            });
        } catch (RuntimeException e) {
            stored.forEach(t -> deleteTrack(t.key()));
            throw e;
        }
    }

    private static AuxException quotaExceeded() {
        return new AuxException(HttpStatus.CONTENT_TOO_LARGE, "STORAGE_QUOTA_EXCEEDED",
                "Uploads are limited to 2GB per user", "mp3s");
    }

    public void deleteTrack(String key) {
        deleteQuietly(audioR2, audioBucket, key);
    }

    // Only hand these out after checking the user may play the track (owner, or a playlist they can see)
    public String signedAudioUrl(String key) {
        return presigner.presignGetObject(p -> p
                        .signatureDuration(SIGNED_URL_TTL)
                        .getObjectRequest(get -> get.bucket(audioBucket).key(key)))
                .url().toString();
    }

    // Prefixes errors with the file name, so the user knows which file of a batch to fix
    private static Mp3 readMp3(MultipartFile file) {
        try {
            return parseMp3(file.getBytes());
        } catch (AuxException e) {
            throw new AuxException(e.getStatus(), e.getDetails().code(),
                    file.getOriginalFilename() + ": " + e.getMessage(), "mp3s");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Walks the MPEG frame chain: checks the format from the bytes (not the filename or Content-Type), sums the
    // exact duration (correct for VBR too), and returns only the frames. The Xing/LAME info frame is a regular
    // frame, so it stays and browsers can still seek in VBR files.
    static Mp3 parseMp3(byte[] bytes) {
        int pos = 0;
        // ID3v2 header: "ID3", version (2), flags (1), tag size as a 28-bit syncsafe int (4); flag 0x10 = footer
        if (bytes.length >= 10 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            int size = (bytes[6] & 0x7F) << 21 | (bytes[7] & 0x7F) << 14 | (bytes[8] & 0x7F) << 7 | (bytes[9] & 0x7F);
            pos = 10 + size + ((bytes[5] & 0x10) != 0 ? 10 : 0);
        }
        // Some taggers pad after the tag with zeros
        while (pos < bytes.length && bytes[pos] == 0) pos++;

        int start = pos;
        int first = 0;
        int sampleRate = 0;
        long samples = 0;
        while (pos + 4 <= bytes.length) {
            int header = ByteBuffer.wrap(bytes, pos, 4).getInt();
            // Sync, version, layer and sample rate must match the first frame; a mismatch is corruption or
            // a random byte pattern that happens to look like a sync word
            if (samples > 0 && (header & 0xFFFE0C00) != (first & 0xFFFE0C00)) break;
            int frameLength = frameLength(header);
            if (frameLength < 0) break;
            if (samples == 0) {
                first = header;
                sampleRate = sampleRate(header);
            }
            if (pos + frameLength > bytes.length) break; // truncated last frame, common in real rips: drop it
            pos += frameLength;
            samples += isMpeg1(header) ? 1152 : 576;
            if (samples > (long) MAX_TRACK_SECONDS * sampleRate) {
                throw new AuxException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LONG",
                        "Track must be at most 10 minutes", "mp3s");
            }
        }

        if (samples == 0) {
            throw new AuxException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO_TYPE",
                    "File must be an MP3", "mp3s");
        }
        if (bytes.length - pos > MAX_TRAILING_BYTES) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO", "MP3 is damaged and could not be read", "mp3s");
        }
        if (samples < (long) MIN_TRACK_SECONDS * sampleRate) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_SHORT",
                    "Track must be at least " + MIN_TRACK_SECONDS + " seconds", "mp3s");
        }
        return new Mp3(Arrays.copyOfRange(bytes, start, pos), (int) Math.round((double) samples / sampleRate));
    }

    // Header bits: 11 sync, 2 version (0 = 2.5, 1 reserved, 2 = MPEG-2, 3 = MPEG-1), 2 layer (1 = Layer III),
    // 1 CRC flag, 4 bitrate index, 2 sample rate index, 1 padding. Returns -1 if this isn't a Layer III frame.
    private static int frameLength(int header) {
        int version = header >>> 19 & 3;
        int kbps = (isMpeg1(header) ? MPEG1_KBPS : MPEG2_KBPS)[header >>> 12 & 0xF];
        if ((header >>> 21 & 0x7FF) != 0x7FF || version == 1 || (header >>> 17 & 3) != 1
                || kbps == 0 || (header >>> 10 & 3) == 3) {
            return -1;
        }
        int bytesPerKbps = isMpeg1(header) ? 144_000 : 72_000;
        return bytesPerKbps * kbps / sampleRate(header) + (header >>> 9 & 1);
    }

    private static boolean isMpeg1(int header) {
        return (header >>> 19 & 3) == 3;
    }

    // Only valid once frameLength has accepted the header
    private static int sampleRate(int header) {
        int base = new int[] {44100, 48000, 32000}[header >>> 10 & 3];
        return switch (header >>> 19 & 3) {
            case 3 -> base;      // MPEG-1
            case 2 -> base / 2;  // MPEG-2
            default -> base / 4; // MPEG-2.5
        };
    }

    private static byte[] readUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "No file uploaded", "file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AuxException(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "Image must be 5MB or smaller", "file");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Returns the new public URL. Order: upload new, then point the user at it, then delete the old one,
    // so a failure at any step never leaves a broken image. save persists the entity setUrl changed.
    private String store(
            String prefix,
            byte[] webp,
            String oldUrl,
            Consumer<String> setUrl,
            Runnable save
    ) {
        // Server-generated key: the client's filename is never used
        String key = prefix + "/" + UUID.randomUUID() + ".webp";
        r2.putObject(put -> put
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/webp")
                        // Keys are never reused, so the content at a URL never changes
                        .cacheControl("public, max-age=31536000, immutable"),
                RequestBody.fromBytes(webp));

        String newUrl = publicBaseUrl + "/" + key;
        setUrl.accept(newUrl);
        try {
            save.run();
        } catch (RuntimeException e) {
            deleteQuietly(r2, bucket, key);
            throw e;
        }

        // Only objects we own; skips the bundled default picture
        if (oldUrl != null && oldUrl.startsWith(publicBaseUrl + "/")) {
            deleteQuietly(r2, bucket, oldUrl.substring(publicBaseUrl.length() + 1));
        }
        return newUrl;
    }

    // Worst case is an orphaned object, which costs storage, not correctness
    private static void deleteQuietly(S3Client client, String bucket, String key) {
        try {
            client.deleteObject(delete -> delete.bucket(bucket).key(key));
        } catch (SdkException e) {
            log.warn("Could not delete R2 object {}", key, e);
        }
    }

    static byte[] toWebp(byte[] bytes) {
        DECODE_SLOTS.acquireUninterruptibly();
        try {
            BufferedImage image = decode(bytes, MIN_SIDE, MIN_SIDE);
            int side = Math.min(OUTPUT_SIDE, Math.min(image.getWidth(), image.getHeight())); // never upscale
            BufferedImage square = Thumbnails.of(image)
                    .crop(Positions.CENTER)
                    .size(side, side)
                    .imageType(image.getType())
                    .asBufferedImage();
            return encodeWebp(square);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // decode errors are already AuxExceptions; this is our side
        } finally {
            DECODE_SLOTS.release();
        }
    }

    static byte[] bannerToWebp(byte[] bytes, int x, int y, int width, int height) {
        DECODE_SLOTS.acquireUninterruptibly();
        try {
            BufferedImage image = decode(bytes, BANNER_MIN_WIDTH, BANNER_MIN_HEIGHT);
            // Frontend rounds to whole pixels, so 3:1 holds only within a few pixels
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || (long) x + width > image.getWidth() || (long) y + height > image.getHeight()
                    || Math.abs(width - 3 * height) > 3) {
                throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_CROP",
                        "Crop must be a 3:1 area inside the image", "cropWidth");
            }
            // getSubimage is a view onto the original pixels; the WebP encoder ignores its offset and reads garbage,
            // so copy the area into its own image first
            BufferedImage crop = new BufferedImage(width, height,
                    image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = crop.createGraphics();
            g.drawImage(image.getSubimage(x, y, width, height), 0, 0, null);
            g.dispose();
            double scale = Math.min(1.0, (double) BANNER_MAX_WIDTH / width); // never upscale
            if (scale < 1.0) {
                crop = Thumbnails.of(crop)
                        .forceSize(BANNER_MAX_WIDTH, (int) Math.round(height * scale))
                        .imageType(image.getType())
                        .asBufferedImage();
            }
            return encodeWebp(crop);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            DECODE_SLOTS.release();
        }
    }

    private static BufferedImage decode(byte[] bytes, int minWidth, int minHeight) {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            // Picks a reader by magic bytes, not by the client's Content-Type or filename
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw unsupported();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) throw unsupported();
                reader.setInput(in);
                // Readers report corrupt/truncated data as warnings and return a half-gray image
                reader.addIIOReadWarningListener((source, warning) -> { throw invalid(); });

                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > MAX_PIXELS) {
                    throw new AuxException(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE",
                            "Image must be at most 25 megapixels", "file");
                }

                // PNG orientation (eXIf chunk) is rare enough to ignore
                Orientation orientation = format.equals("jpeg") ? ExifUtils.getExifOrientation(reader, 0) : null;
                BufferedImage image = toSrgb(reader.read(0));
                // Must happen before metadata is dropped, or phone photos come out sideways
                BufferedImage oriented = orientation == null ? image : ExifFilterUtils.getFilterForOrientation(orientation).apply(image);
                // After rotation: a portrait phone photo has swapped sides
                if (oriented.getWidth() < minWidth || oriented.getHeight() < minHeight) {
                    throw new AuxException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_SMALL",
                            "Image must be at least " + minWidth + "x" + minHeight + " pixels", "file");
                }
                return oriented;
            } finally {
                reader.dispose();
            }
        } catch (AuxException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Malformed input can surface as almost any exception from ImageIO/ICC code
            throw invalid();
        }
    }

    // Wide-gamut photos (iPhone Display P3) look washed out if their ICC profile is dropped unconverted.
    // Also normalizes gray/indexed/BGR layouts to INT_RGB or INT_ARGB for the encoder.
    private static BufferedImage toSrgb(BufferedImage image) {
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (image.getType() == type && image.getColorModel().getColorSpace().isCS_sRGB()) return image;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), type);
        return new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null).filter(image, out);
    }

    private static byte[] encodeWebp(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(CompressionType.Lossy); // lossless photos run to megabytes
            param.setCompressionQuality(0.9f);
            param.setMethod(6); // slowest encode, best quality per byte
            param.setUseSharpYUV(true); // keeps saturated edges crisp under 4:2:0 chroma
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static AuxException unsupported() {
        return new AuxException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE",
                "Image must be a JPEG or PNG", "file");
    }

    private static AuxException invalid() {
        return new AuxException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Image could not be read", "file");
    }
}
