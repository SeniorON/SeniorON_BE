package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadCompleteRequest;
import com.example.senioron.domain.family.dto.request.FamilyPhotoUploadUrlRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoAlbumResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCursorResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoListResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoUploadUrlResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.projection.FamilyPhotoAlbumCountProjection;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.PresignedUploadInfo;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.StoredObjectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPhotoService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final FamilyPhotoPermissionService familyPhotoPermissionService;
    private final FamilyPhotoPersistenceService photoPersistenceService;

    private static final int NEW_PHOTO_WINDOW_HOURS = 24;
    private static final long MAX_FAMILY_PHOTO_SIZE = 10L * 1024 * 1024;

    private static final Map<String, String> IMAGE_EXTENSIONS =
            Map.of(
                    "image/jpeg", ".jpg",
                    "image/png", ".png",
                    "image/webp", ".webp"
            );

    private static final Pattern IMAGE_FILE_NAME_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{8}-"
                            + "[0-9a-f]{4}-"
                            + "[0-9a-f]{4}-"
                            + "[0-9a-f]{4}-"
                            + "[0-9a-f]{12}"
                            + "\\.(jpg|png|webp)$"
            );

    @Transactional(readOnly = true)
    public FamilyPhotoUploadUrlResponse createPhotoUploadUrl(
            User principal,
            FamilyPhotoUploadUrlRequest request
    ) {
        User user = userRepository
                .findByIdWithFamily(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        String directory = "family-photos/"
                + family.getFamilyId()
                + "/"
                + user.getUsersId();

        PresignedUploadInfo uploadInfo =
                s3Service.createPresignedUploadUrl(
                        directory,
                        request.getContentType()
                );

        return FamilyPhotoUploadUrlResponse.builder()
                .imageKey(uploadInfo.imageKey())
                .uploadUrl(uploadInfo.uploadUrl())
                .expiresInSeconds(
                        uploadInfo.expiresInSeconds()
                )
                .build();
    }

    public FamilyPhotoItemResponse completePhotoUpload(
            User principal,
            String idempotencyKey,
            FamilyPhotoUploadCompleteRequest request
    ) {
        User user = userRepository
                .findByIdWithFamily(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        LocalDateTime newPhotoCutoff =
                LocalDateTime.now()
                        .minusHours(
                                NEW_PHOTO_WINDOW_HOURS
                        );

        /*
         * 완료 응답을 받지 못한 클라이언트가
         * 같은 멱등키로 재요청한 경우 기존 결과를 반환한다.
         */
        FamilyPhoto existingByIdempotencyKey =
                photoPersistenceService
                        .findExisting(
                                user.getUsersId(),
                                idempotencyKey
                        )
                        .orElse(null);

        if (existingByIdempotencyKey != null) {
            return toItemResponse(
                    existingByIdempotencyKey,
                    user,
                    newPhotoCutoff
            );
        }

        String imageKey = request.getImageKey();

        /*
         * 다른 가족 또는 다른 사용자의 key인지 먼저 검사한다.
         * 이 검사를 먼저 해야 다른 사용자의 imageKey 등록 여부가
         * 외부에 노출되지 않는다.
         */
        validateImageKeyOwnership(
                user,
                family,
                imageKey
        );

        if (familyPhotoRepository.existsByImageKey(imageKey)) {
            throw new BusinessException(
                    ErrorCode.FAMILY_PHOTO_ALREADY_REGISTERED
            );
        }

        /*
         * 실제 S3 객체의 존재 여부, 크기,
         * Content-Type과 확장자를 검사한다.
         */
        validateUploadedObject(
                user,
                family,
                imageKey
        );

        try {
            FamilyPhoto savedPhoto =
                    photoPersistenceService.create(
                            user.getUsersId(),
                            imageKey,
                            idempotencyKey,
                            request.getDescription()
                    );

            return toItemResponse(
                    savedPhoto,
                    user,
                    newPhotoCutoff
            );
        } catch (DataIntegrityViolationException exception) {
            /*
             * 동일 멱등키 요청이 동시에 실행된 경우,
             * 먼저 저장된 결과를 반환한다.
             */
            FamilyPhoto existingPhoto =
                    photoPersistenceService
                            .findExisting(
                                    user.getUsersId(),
                                    idempotencyKey
                            )
                            .orElse(null);

            if (existingPhoto != null) {
                return toItemResponse(
                        existingPhoto,
                        user,
                        newPhotoCutoff
                );
            }

            /*
             * 서로 다른 멱등키로 동일 imageKey를
             * 동시에 완료한 경우 409를 반환한다.
             */
            if (familyPhotoRepository
                    .existsByImageKey(imageKey)) {
                throw new BusinessException(
                        ErrorCode.FAMILY_PHOTO_ALREADY_REGISTERED
                );
            }

            throw exception;
        }
    }

    private StoredObjectInfo validateUploadedObject(
            User user,
            Family family,
            String imageKey
    ) {
        validateImageKeyOwnership(
                user,
                family,
                imageKey
        );

        StoredObjectInfo objectInfo =
                s3Service.findObjectInfo(imageKey)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.FAMILY_PHOTO_UPLOAD_NOT_FOUND
                                )
                        );

        if (objectInfo.contentLength() <= 0
                || objectInfo.contentLength()
                > MAX_FAMILY_PHOTO_SIZE) {
            deleteUploadedObjectSafely(imageKey);

            throw new BusinessException(
                    ErrorCode.FAMILY_PHOTO_SIZE_EXCEEDED
            );
        }

        String expectedExtension =
                IMAGE_EXTENSIONS.get(
                        objectInfo.contentType()
                );

        if (expectedExtension == null
                || !imageKey.endsWith(expectedExtension)) {
            deleteUploadedObjectSafely(imageKey);

            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }

        return objectInfo;
    }

    private void validateImageKeyOwnership(
            User user,
            Family family,
            String imageKey
    ) {
        String expectedPrefix =
                "family-photos/"
                        + family.getFamilyId()
                        + "/"
                        + user.getUsersId()
                        + "/";

        if (imageKey == null
                || !imageKey.startsWith(expectedPrefix)) {
            throw new BusinessException(
                    ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN
            );
        }

        String fileName =
                imageKey.substring(
                        expectedPrefix.length()
                );

        if (!IMAGE_FILE_NAME_PATTERN
                .matcher(fileName)
                .matches()) {
            throw new BusinessException(
                    ErrorCode.FAMILY_PHOTO_UPLOAD_FORBIDDEN
            );
        }
    }

    public FamilyPhotoItemResponse createPhoto(
            User principal,
            String idempotencyKey,
            FamilyPhotoCreateRequest request
    ) {
        User user = userRepository.findByIdWithFamily(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Family family = user.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        LocalDateTime newPhotoCutoff =
                LocalDateTime.now().minusHours(NEW_PHOTO_WINDOW_HOURS);

        return photoPersistenceService
                .findExisting(user.getUsersId(), idempotencyKey)
                .map(photo ->
                        toItemResponse(photo, user, newPhotoCutoff)
                )
                .orElseGet(() ->
                        uploadAndCreatePhoto(
                                user,
                                family,
                                idempotencyKey,
                                request,
                                newPhotoCutoff
                        )
                );
    }

    private FamilyPhotoItemResponse uploadAndCreatePhoto(
            User user,
            Family family,
            String idempotencyKey,
            FamilyPhotoCreateRequest request,
            LocalDateTime newPhotoCutoff
    ) {
        String directory =
                "family-photos/" + family.getFamilyId();

        String imageKey = s3Service.upload(
                request.getImage(),
                directory
        );

        FamilyPhoto savedPhoto;

        try {
            savedPhoto = photoPersistenceService.create(
                    user.getUsersId(),
                    imageKey,
                    idempotencyKey,
                    request.getDescription()
            );

        } catch (DataIntegrityViolationException exception) {
            deleteUploadedObjectSafely(imageKey);

            FamilyPhoto existingPhoto =
                    photoPersistenceService
                            .findExisting(
                                    user.getUsersId(),
                                    idempotencyKey
                            )
                            .orElseThrow(() -> exception);

            return toItemResponse(
                    existingPhoto,
                    user,
                    newPhotoCutoff
            );

        } catch (RuntimeException exception) {
            deleteUploadedObjectSafely(imageKey);
            throw exception;
        }

        return toItemResponse(
                savedPhoto,
                user,
                newPhotoCutoff
        );
    }

    private void deleteUploadedObjectSafely(String imageKey) {
        try {
            s3Service.delete(imageKey);
        } catch (RuntimeException exception) {
            log.error(
                    "가족사진 멱등 처리 중 S3 객체 삭제 실패, imageKey={}",
                    imageKey,
                    exception
            );
        }
    }

    private User findFamilyChildUploader(
            Long uploaderUserId,
            Family family
    ) {
        User uploader = userRepository.findById(uploaderUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND));

        boolean sameFamily = uploader.getFamily() != null
                && Objects.equals(
                uploader.getFamily().getFamilyId(),
                family.getFamilyId()
        );

        if (!sameFamily || uploader.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND);
        }

        return uploader;
    }

    private FamilyPhotoItemResponse toItemResponse(
            FamilyPhoto photo,
            User currentUser,
            LocalDateTime newPhotoCutoff
    ) {
        boolean newPhoto =
                currentUser.getRole() == Role.PARENT
                        && photo.getUser().getRole() == Role.CHILD
                        && !photo.isViewedByParent()
                        && !photo.getCreatedAt().isBefore(newPhotoCutoff);

        return FamilyPhotoItemResponse.builder()
                .familyPhotoId(photo.getFamilyPhotoId())
                .imageUrl(s3Service.getFileUrl(photo.getImageKey()))
                .uploaderUserId(photo.getUser().getUsersId())
                .uploaderName(photo.getUser().getName())
                .description(photo.getDescription())
                .canDelete(
                        familyPhotoPermissionService.canDelete(
                                photo,
                                currentUser
                        )
                )
                .createdAt(photo.getCreatedAt())
                .newPhoto(newPhoto)
                .build();
    }

    @Transactional(readOnly = true)
    public FamilyPhotoListResponse getPhotos(
            User principal,
            Long uploaderUserId,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException(
                    "사진 조회 개수는 1~50이어야 합니다."
            );
        }

        if ((cursorCreatedAt == null) != (cursorId == null)) {
            throw new IllegalArgumentException(
                    "cursorCreatedAt과 cursorId는 함께 전달해야 합니다."
            );
        }

        User currentUser = userRepository.findById(
                        principal.getUsersId()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        Family family = currentUser.getFamily();

        if (family == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        /*
         * uploaderUserId를 사용한 자녀 앨범 조회는
         * 부모 화면에서만 허용한다.
         */
        if (uploaderUserId != null
                && currentUser.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User uploader = uploaderUserId == null
                ? null
                : findFamilyChildUploader(
                uploaderUserId,
                family
        );

        Pageable pageable = PageRequest.of(0, size + 1);

        List<FamilyPhoto> fetchedPhotos;

        if (uploader == null) {
            if (cursorCreatedAt == null) {
                fetchedPhotos = familyPhotoRepository
                        .findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                                family,
                                pageable
                        );
            } else {
                fetchedPhotos =
                        familyPhotoRepository.findNextPageByCursor(
                                family,
                                cursorCreatedAt,
                                cursorId,
                                pageable
                        );
            }
        } else {
            if (cursorCreatedAt == null) {
                fetchedPhotos = familyPhotoRepository
                        .findByFamilyAndUserOrderByCreatedAtDescFamilyPhotoIdDesc(
                                family,
                                uploader,
                                pageable
                        );
            } else {
                fetchedPhotos = familyPhotoRepository
                        .findNextPageByUploaderAndCursor(
                                family,
                                uploader,
                                cursorCreatedAt,
                                cursorId,
                                pageable
                        );
            }
        }

        boolean hasNext = fetchedPhotos.size() > size;

        List<FamilyPhoto> pagePhotos = hasNext
                ? fetchedPhotos.subList(0, size)
                : fetchedPhotos;

        FamilyPhotoCursorResponse nextCursor = null;

        if (hasNext) {
            FamilyPhoto lastPhoto =
                    pagePhotos.get(pagePhotos.size() - 1);

            nextCursor = FamilyPhotoCursorResponse.builder()
                    .createdAt(lastPhoto.getCreatedAt())
                    .familyPhotoId(lastPhoto.getFamilyPhotoId())
                    .build();
        }

        LocalDateTime newPhotoCutoff =
                LocalDateTime.now().minusHours(NEW_PHOTO_WINDOW_HOURS);

        List<FamilyPhotoItemResponse> photoResponses =
                pagePhotos.stream()
                        .map(photo ->
                                toItemResponse(
                                        photo,
                                        currentUser,
                                        newPhotoCutoff
                                )
                        )
                        .toList();

        long totalCount = uploader == null
                ? familyPhotoRepository.countByFamily(family)
                : familyPhotoRepository.countByFamilyAndUser(
                family,
                uploader
        );

        return FamilyPhotoListResponse.builder()
                .photos(photoResponses)
                .totalCount(totalCount)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    @Transactional
    public void deletePhoto(User principal, Long familyPhotoId) {
        User currentUser = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = currentUser.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyPhoto photo = familyPhotoRepository
                .findByFamilyPhotoIdAndFamily(familyPhotoId, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        if (!familyPhotoPermissionService.canDelete(photo, currentUser)) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_DELETE_FORBIDDEN);
        }

        String imageKey = photo.getImageKey();

        familyPhotoRepository.delete(photo);
        registerS3DeleteAfterCommit(imageKey);
    }

    private void registerS3DeleteAfterCommit(String imageKey){
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization(){
                    @Override
                    public void afterCommit(){
                        try{
                            s3Service.delete(imageKey);
                        }catch(RuntimeException e){
                            log.warn(
                                    "가족 사진 S3 삭제 실패, imageKey={}",
                                    imageKey,
                                    e
                            );
                        }
                    }
                }
        );
    }

    @Transactional(readOnly = true)
    public List<FamilyPhotoAlbumResponse> getPhotoAlbums(
            User principal
    ){
        // 인증 객체의 id를 사용해 현재 사용자 정보를 db에서 다시 조회
        User parent = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 부모님 전용 화면이므로 PARENT 계정만 허용
        if (parent.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 요청으로 familyId 대신 로그인 부모의 가족을 사용
        Family family = parent.getFamily();

        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        // 현재 시각으로부터 24시간 전을 새로운 사진 판단 기준으로 사용
        LocalDateTime newPhotoCutoff = LocalDateTime.now().minusHours(NEW_PHOTO_WINDOW_HOURS);

        // 자녀마다 가장 최근에 올린 사진 한 장 조회
        List<FamilyPhoto> latestPhotos = familyPhotoRepository.findLatestPhotosByUploader(
                family,
                Role.CHILD
        );

        if (latestPhotos.isEmpty()) {
            return List.of();
        }

        // 자녀별 전체 사진 수와 새로운 사진 수를 조회
        Map<Long, FamilyPhotoAlbumCountProjection> countMap =
                familyPhotoRepository.countAlbumPhotosByUploader(
                                family,
                                Role.CHILD,
                                newPhotoCutoff
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        FamilyPhotoAlbumCountProjection
                                                ::getUploaderUserId,
                                        Function.identity()
                                )
                        );

        // 최신 사진과 사진 개수 집계 결과를 자녀 ID 기준으로 조합
        return latestPhotos.stream()
                .map(latestPhoto -> {
                    User uploader = latestPhoto.getUser();

                    FamilyPhotoAlbumCountProjection count = countMap.get(uploader.getUsersId());

                    long photoCount = count == null
                            ? 0L
                            : count.getPhotoCount();

                    long newPhotoCount = count == null
                            ? 0L
                            : count.getNewPhotoCount();

                    return FamilyPhotoAlbumResponse.builder()
                            .uploaderUserId(uploader.getUsersId())
                                    .uploaderName(uploader.getName())
                                    .latestPhotoUrl(s3Service.getFileUrl(latestPhoto.getImageKey()))
                                    .photoCount(photoCount)
                                    .hasNewPhotos(newPhotoCount > 0)
                                    .build();
                })
                .toList();

    }

    // 사진 한 장 확인 처리 Service 추가
    @Transactional
    public void markPhotoAsViewed(
            User principal,
            Long familyPhotoId
    ) {
        User parent = userRepository.findById(
                        principal.getUsersId()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        if (parent.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Family family = parent.getFamily();

        if (family == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }

        FamilyPhoto photo = familyPhotoRepository
                .findByFamilyPhotoIdAndFamily(
                        familyPhotoId,
                        family
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.FAMILY_PHOTO_NOT_FOUND
                        )
                );

        photo.markAsViewedByParent();
    }

    @Transactional(readOnly = true)
    public FamilyPhotoItemResponse getPhoto(
            User principal,
            Long familyPhotoId
    ) {
        User currentUser = userRepository
                .findByIdWithFamily(principal.getUsersId())
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Family family = currentUser.getFamily();

        if(family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        FamilyPhoto photo = familyPhotoRepository
                .findByFamilyPhotoIdAndFamily(
                        familyPhotoId,
                        family
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        LocalDateTime newPhotoCutoff = LocalDateTime.now().minusHours(NEW_PHOTO_WINDOW_HOURS);

        return toItemResponse(
                photo,
                currentUser,
                newPhotoCutoff
        );
    }
}
