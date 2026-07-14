package com.example.senioron.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(
            MultipartFile file,
            String directory
    ) {
        validateImage(file);

        String contentType = file.getContentType();
        String extension = IMAGE_EXTENSIONS.get(contentType);

        String key = directory
                + "/"
                + UUID.randomUUID()
                + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            inputStream,
                            file.getSize()
                    )
            );

            return key;
        } catch (IOException | SdkException e) {
            throw new IllegalStateException(
                    "사진 업로드에 실패했습니다.",
                    e
            );
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "사진은 필수입니다."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "사진 크기는 10MB 이하여야 합니다."
            );
        }

        String contentType = file.getContentType();

        if (contentType == null
                || !IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException(
                    "JPG, PNG, WEBP 형식만 업로드할 수 있습니다."
            );
        }
    }
}
