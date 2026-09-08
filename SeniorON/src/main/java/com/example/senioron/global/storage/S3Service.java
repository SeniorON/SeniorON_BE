package com.example.senioron.global.storage;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Duration PRESIGNED_UPLOAD_DURATION = Duration.ofMinutes(5);

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region}")
    private String region;

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

    public void delete(String imageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        s3Client.deleteObject(request);
    }

    public String getFileUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(1))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();
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

        validateImageContentType(file.getContentType());
    }

    public PresignedUploadInfo createPresignedUploadUrl(
            String directory,
            String contentType
    ) {
        validateImageContentType(contentType);

        String extension = IMAGE_EXTENSIONS.get(contentType);

        String imageKey = directory
                + "/"
                + UUID.randomUUID()
                + extension;

        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(imageKey)
                        .contentType(contentType)
                        .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGNED_UPLOAD_DURATION)
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        return new PresignedUploadInfo(
                imageKey,
                presignedRequest.url().toString(),
                PRESIGNED_UPLOAD_DURATION.toSeconds()
        );
    }

    public Optional<StoredObjectInfo> findObjectInfo(
            String imageKey
    ) {
        if (imageKey == null || imageKey.isBlank()) {
            return Optional.empty();
        }

        HeadObjectRequest request =
                HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(imageKey)
                        .build();

        try {
            HeadObjectResponse response =
                    s3Client.headObject(request);

            return Optional.of(
                    new StoredObjectInfo(
                            response.contentLength(),
                            response.contentType()
                    )
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }

            throw new IllegalStateException(
                    "S3 객체 정보를 조회하지 못했습니다.",
                    exception
            );
        } catch (SdkException exception) {
            throw new IllegalStateException(
                    "S3 객체 정보를 조회하지 못했습니다.",
                    exception
            );
        }
    }

    private void validateImageContentType(String contentType) {
        if (contentType == null
                || !IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }
    }
}
