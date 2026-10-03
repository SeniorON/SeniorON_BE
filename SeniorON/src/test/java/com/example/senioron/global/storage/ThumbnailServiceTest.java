package com.example.senioron.global.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailServiceTest {

    private final ThumbnailService service = new ThumbnailService();

    @ParameterizedTest
    @CsvSource({
            "jpg, 1200, 600, 600, 300",
            "jpg, 600, 1200, 300, 600",
            "png, 1000, 1000, 600, 600"
    })
    void convertsImagesToJpegWithinBoundsAndPreservesAspectRatio(
            String format, int width, int height, int expectedWidth, int expectedHeight
    ) throws Exception {
        byte[] original = imageBytes(width, height, format);
        byte[] unchangedOriginal = original.clone();

        byte[] thumbnail = service.create(original);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(expectedWidth);
        assertThat(decoded.getHeight()).isEqualTo(expectedHeight);
        assertThat(thumbnail[0] & 0xff).isEqualTo(0xff);
        assertThat(thumbnail[1] & 0xff).isEqualTo(0xd8);
        assertThat(original).isEqualTo(unchangedOriginal);
    }

    @Test
    void fillsTransparentPngBackgroundWithWhite() throws Exception {
        BufferedImage transparent = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(transparent, "png", output)).isTrue();

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(
                service.create(output.toByteArray())
        ));

        assertThat(new Color(thumbnail.getRGB(300, 300))).isEqualTo(Color.WHITE);
        assertThat(thumbnail.getColorModel().hasAlpha()).isFalse();
    }

    @Test
    void appliesExifOrientationBeforeReturningThumbnail() throws Exception {
        byte[] jpeg = imageBytes(1200, 600, "jpg");
        // JPEG APP1: little-endian EXIF orientation 6 (90 degrees clockwise).
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                6, 0, 0, 0, 0, 0, 0, 0
        };
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        original.write(jpeg, 0, 2);
        original.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, (byte) (exif.length + 2)});
        original.write(exif);
        original.write(jpeg, 2, jpeg.length - 2);

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(
                service.create(original.toByteArray())
        ));

        assertThat(thumbnail.getWidth()).isEqualTo(300);
        assertThat(thumbnail.getHeight()).isEqualTo(600);
    }

    @Test
    void decodesWebpAndReturnsJpeg() throws Exception {
        byte[] webp = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA"
        );

        byte[] thumbnail = service.create(webp);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isBetween(1, 600);
        assertThat(decoded.getHeight()).isBetween(1, 600);
        assertThat(thumbnail[0] & 0xff).isEqualTo(0xff);
        assertThat(thumbnail[1] & 0xff).isEqualTo(0xd8);
    }

    @Test
    void rejectsUnreadableImage() {
        assertThatThrownBy(() -> service.create(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("썸네일 생성에 실패했습니다.");
    }

    private byte[] imageBytes(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
}
