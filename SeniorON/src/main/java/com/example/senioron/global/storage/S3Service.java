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
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    // S3 DeleteObjects API의 요청당 키 개수 제한. 호출자가 이 크기로 배치를 구성한다.
    private static final int MAX_DELETE_OBJECT_KEYS = 1000;
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

    // HTTP 200이어도 개별 키 삭제가 실패할 수 있으므로 실패 키와 사유를 반환한다.
    public Map<String, String> deleteObjects(List<String> imageKeys) {
        if (imageKeys.size() > MAX_DELETE_OBJECT_KEYS) {
            throw new IllegalArgumentException("S3 일괄 삭제는 한 번에 1000개 키까지 가능합니다.");
        }
        if (imageKeys.isEmpty()) {
            return Map.of();
        }
        // null/blank 키 정리는 큐 등록 시 S3DeletionService.enqueue에서 수행한다.
        List<ObjectIdentifier> objects = imageKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build()).toList();
        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
        Map<String, String> failures = new LinkedHashMap<>();
        s3Client.deleteObjects(request).errors().forEach(error ->
                failures.put(error.key(), error.code() + ": " + error.message())
        );
        return Map.copyOf(failures);
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

    // 저장된 원본 이미지 읽기
    public byte[] download(String imageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        try {
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (SdkException exception) {
            throw new IllegalStateException(
                    "원본 이미지 다운로드에 실패했습니다.",
                    exception
            );
        }
    }

    // 생성된 JPEG 썸네일 저장
    public String uploadThumbnail(byte[] thumbnailBytes) {
        if (thumbnailBytes == null || thumbnailBytes.length == 0) {
            throw new IllegalArgumentException("썸네일 이미지가 필요합니다.");
        }

        String thumbnailKey = "family-photos/thumbnails/"
                + UUID.randomUUID()
                + ".jpg";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(thumbnailKey)
                .contentType("image/jpeg")
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(thumbnailBytes)
            );
            return thumbnailKey;
        } catch (SdkException exception) {
            throw new IllegalStateException(
                    "썸네일 저장에 실패했습니다.",
                    exception
            );
        }
    }
}
