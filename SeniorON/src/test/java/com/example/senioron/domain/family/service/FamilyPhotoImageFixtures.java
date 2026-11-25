package com.example.senioron.domain.family.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

final class FamilyPhotoImageFixtures {
    private FamilyPhotoImageFixtures() {}

    static byte[] image(String format) {
        if (format.equals("webp")) {
            return Base64.getDecoder().decode(
                    "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, output)) {
                throw new IllegalStateException("Missing image writer: " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
