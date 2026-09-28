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
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

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
import org.springframework.web.multipart.MultipartFile;

import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;
import com.luciad.imageio.webp.CompressionType;
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

// Validates user image uploads and stores them in R2.
// Nothing the client sends is stored as-is: the image is decoded, normalized to sRGB, rotated per EXIF,
// center-cropped, and re-encoded as WebP. That strips metadata (GPS etc.) and any non-image payload
// (polyglot files, trailing data), so the bucket only ever holds pixels we produced.
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

    // TODO: caps decode memory at ~4 x 100MB (25MP ARGB); tune with heap size or move to a queue
    private static final Semaphore DECODE_SLOTS = new Semaphore(4);

    private final S3Client r2;
    private final String bucket;
    private final String publicBaseUrl;
    private final UserRepository users;

    // aux.r2.* come from AUX_R2_* in .env (see application.properties)
    public UploadService(
            @Value("${aux.r2.account-id}") String accountId,
            @Value("${aux.r2.access-key-id}") String accessKeyId,
            @Value("${aux.r2.secret-access-key}") String secretAccessKey,
            @Value("${aux.r2.bucket}") String bucket,
            @Value("${aux.r2.public-base-url}") String publicBaseUrl,
            UserRepository users
    ) {
        this.r2 = S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                // R2 doesn't support every default checksum newer SDKs send
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.users = users;
    }

    public String replaceProfilePicture(UserEntity user, MultipartFile file) {
        byte[] webp = toWebp(readUpload(file));
        return store(user, "pfp", webp, user.getProfilePictureUrl(), user::setProfilePictureUrl);
    }

    // Crop is in pixels of the EXIF-rotated original, i.e. what the browser shows the user
    public String replaceBanner(UserEntity user, MultipartFile file, int x, int y, int width, int height) {
        byte[] webp = bannerToWebp(readUpload(file), x, y, width, height);
        return store(user, "banner", webp, user.getBannerUrl(), user::setBannerUrl);
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
    // so a failure at any step never leaves the user with a broken image.
    private String store(UserEntity user, String prefix, byte[] webp, String oldUrl, Consumer<String> setUrl) {
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
            users.save(user);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }

        // Only objects we own; skips the bundled default picture
        if (oldUrl != null && oldUrl.startsWith(publicBaseUrl + "/")) {
            deleteQuietly(oldUrl.substring(publicBaseUrl.length() + 1));
        }
        return newUrl;
    }

    // Worst case is an orphaned object, which costs storage, not correctness
    private void deleteQuietly(String key) {
        try {
            r2.deleteObject(delete -> delete.bucket(bucket).key(key));
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
