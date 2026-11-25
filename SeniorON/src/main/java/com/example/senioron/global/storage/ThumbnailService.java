package com.example.senioron.global.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Locale;
import java.util.Set;

@Service
public class ThumbnailService {

    private static final int MAX_SIZE = 600;
    private static final double QUALITY = 0.8;

    public void validateOriginal(byte[] originalBytes) {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(originalBytes))) {
            var readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
            }

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);

                if (!Set.of("jpeg", "png", "webp").contains(format)) {
                    throw new BusinessException(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
                }

                reader.setInput(input);
                BufferedImage image = reader.read(0);

                if (image == null) {
                    throw new BusinessException(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_INVALID_IMAGE);
        }
    }

    public byte[] create(byte[] originalBytes) {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("원본 이미지가 필요합니다.");
        }

        try (
                ByteArrayInputStream input =
                        new ByteArrayInputStream(originalBytes);
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {
            Thumbnails.of(input)
                    .size(MAX_SIZE, MAX_SIZE)
                    .keepAspectRatio(true)
                    .useExifOrientation(true)
                    .addFilter(this::toRgb)
                    .outputFormat("jpg")
                    .outputQuality(QUALITY)
                    .toOutputStream(output);

            return output.toByteArray();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "썸네일 생성에 실패했습니다.",
                    exception
            );
        }
    }

    private BufferedImage toRgb(BufferedImage image) {
        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(
                    0, 0,
                    image.getWidth(), image.getHeight()
            );
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return result;
    }
}