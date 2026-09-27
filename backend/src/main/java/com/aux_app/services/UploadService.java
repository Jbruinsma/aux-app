package com.aux_app.services;

import java.awt.color.ColorSpace;
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

    // Returns the new public URL. Order: upload new, then point the user at it, then delete the old one,
    // so a failure at any step never leaves the user with a broken picture.
    public String replaceProfilePicture(UserEntity user, MultipartFile file) {
        if (file.isEmpty()) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "No file uploaded", "file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AuxException(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "Image must be 5MB or smaller", "file");
        }

        byte[] webp;
        try {
            webp = toWebp(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Server-generated key: the client's filename is never used
        String key = "pfp/" + UUID.randomUUID() + ".webp";
        r2.putObject(put -> put
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/webp")
                        // Keys are never reused, so the content at a URL never changes
                        .cacheControl("public, max-age=31536000, immutable"),
                RequestBody.fromBytes(webp));

        String oldUrl = user.getProfilePictureUrl();
        String newUrl = publicBaseUrl + "/" + key;
        user.setProfilePictureUrl(newUrl);
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
            BufferedImage image = decode(bytes);
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

    private static BufferedImage decode(byte[] bytes) {
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
                if (Math.min(width, height) < MIN_SIDE) {
                    throw new AuxException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_SMALL",
                            "Image must be at least " + MIN_SIDE + "x" + MIN_SIDE + " pixels", "file");
                }

                // PNG orientation (eXIf chunk) is rare enough to ignore
                Orientation orientation = format.equals("jpeg") ? ExifUtils.getExifOrientation(reader, 0) : null;
                BufferedImage image = toSrgb(reader.read(0));
                // Must happen before metadata is dropped, or phone photos come out sideways
                return orientation == null ? image : ExifFilterUtils.getFilterForOrientation(orientation).apply(image);
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
