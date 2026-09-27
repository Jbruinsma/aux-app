package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.aux_app.error.AuxException;

class UploadServiceTest {

    @Test
    void largeJpegBecomes1024SquareWebp() throws IOException {
        BufferedImage out = webp(UploadService.toWebp(encode(solid(2000, 1500, Color.RED, false), "jpg")));
        assertEquals(1024, out.getWidth());
        assertEquals(1024, out.getHeight());
    }

    @Test
    void smallPngIsCroppedNotUpscaledAndKeepsAlpha() throws IOException {
        BufferedImage out = webp(UploadService.toWebp(encode(solid(400, 300, new Color(0, 0, 255, 128), true), "png")));
        assertEquals(300, out.getWidth());
        assertEquals(300, out.getHeight());
        assertTrue(out.getColorModel().hasAlpha());
    }

    @Test
    void exifOrientationIsApplied() throws IOException {
        // Left half red, right half blue. Orientation 6 = rotate 90° clockwise, so red ends up on top
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 300);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 200, 300);
        g.dispose();

        BufferedImage out = webp(UploadService.toWebp(withExifOrientation(encode(image, "jpg"), 6)));
        Color top = new Color(out.getRGB(out.getWidth() / 2, 10));
        assertTrue(top.getRed() > 200 && top.getBlue() < 60, "expected red on top, got " + top);
    }

    @Test
    void decompressionBombIsRejectedBeforeDecoding() throws IOException {
        byte[] png = encode(solid(300, 300, Color.RED, false), "png");
        // Rewrite the IHDR to claim 20000x20000, with a valid CRC so only the size check can stop it
        ByteBuffer.wrap(png, 16, 8).putInt(20000).putInt(20000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        assertRejected(png, HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE");
    }

    @Test
    void htmlNamedPngIsRejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertRejected(html, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE");
    }

    @Test
    void gifIsRejected() throws IOException {
        assertRejected(encode(solid(300, 300, Color.RED, false), "gif"), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE");
    }

    @Test
    void truncatedJpegIsRejected() throws IOException {
        byte[] jpeg = encode(solid(1000, 1000, Color.RED, false), "jpg");
        assertRejected(Arrays.copyOf(jpeg, jpeg.length / 2), HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
    }

    @Test
    void tinyImageIsRejected() throws IOException {
        assertRejected(encode(solid(100, 100, Color.RED, false), "png"), HttpStatus.BAD_REQUEST, "IMAGE_TOO_SMALL");
    }

    private static void assertRejected(byte[] bytes, HttpStatus status, String code) {
        AuxException e = assertThrows(AuxException.class, () -> UploadService.toWebp(bytes));
        assertEquals(status, e.getStatus());
        assertEquals(code, e.getDetails().code());
    }

    private static BufferedImage solid(int width, int height, Color color, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage webp(byte[] bytes) throws IOException {
        assertTrue(bytes.length > 12 && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"));
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // Inserts a minimal big-endian EXIF APP1 segment holding only the Orientation tag, after the JFIF APP0
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        ByteBuffer app1 = ByteBuffer.allocate(2 + 2 + 6 + 8 + 2 + 12 + 4);
        app1.putShort((short) 0xFFE1).putShort((short) (app1.capacity() - 2));
        app1.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        app1.put(new byte[] {'M', 'M', 0, 42}).putInt(8);          // TIFF header, IFD0 at offset 8
        app1.putShort((short) 1);                                  // one entry
        app1.putShort((short) 0x0112).putShort((short) 3).putInt(1) // Orientation, SHORT, count 1
                .putShort((short) orientation).putShort((short) 0);
        app1.putInt(0);                                            // no next IFD

        int at = 4 + ((jpeg[4] & 0xFF) << 8 | (jpeg[5] & 0xFF)); // SOI + APP0 marker + APP0 length
        byte[] out = new byte[jpeg.length + app1.capacity()];
        System.arraycopy(jpeg, 0, out, 0, at);
        System.arraycopy(app1.array(), 0, out, at, app1.capacity());
        System.arraycopy(jpeg, at, out, at + app1.capacity(), jpeg.length - at);
        return out;
    }
}
