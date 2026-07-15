package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.NameUpdateRequest;
import com.example.senioron.domain.user.dto.request.PasswordChangeRequest;
import com.example.senioron.domain.user.dto.response.CurrentNameResponse;
import com.example.senioron.domain.user.dto.response.NameUpdateResponse;
import com.example.senioron.domain.user.dto.response.PasswordChangeResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageUpdateResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserSettingsService {

    private static final long MAX_PROFILE_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_PROFILE_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public CurrentNameResponse getCurrentName(User principal) {
        validateAuthenticated(principal);

        return CurrentNameResponse.builder()
                .name(principal.getName())
                .build();
    }

    public NameUpdateResponse updateName(User principal, NameUpdateRequest request) {
        validateAuthenticated(principal);

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newName = request.getName().trim();

        if (user.getName().equals(newName)) {
            throw new BusinessException(ErrorCode.NAME_NOT_CHANGED);
        }

        user.updateName(newName);

        return NameUpdateResponse.builder()
                .name(user.getName())
                .build();
    }

    public PasswordChangeResponse changePassword(User principal, PasswordChangeRequest request) {
        validateAuthenticated(principal);

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (!request.getNewPassword().equals(request.getNewPasswordCheck())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));

        return PasswordChangeResponse.builder()
                .changed(true)
                .build();
    }

    public ProfileImageUpdateResponse updateProfileImage(User principal, MultipartFile image) {
        validateAuthenticated(principal);
        validateProfileImage(image);

        User user = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String previousProfileImageKey = user.getProfileImageKey();
        String newProfileImageKey = uploadProfileImage(image, user.getUsersId());
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();

        if (synchronizationActive) {
            registerProfileImageCleanup(previousProfileImageKey, newProfileImageKey);
        }

        try {
            user.updateProfileImageKey(newProfileImageKey);
            userRepository.flush();
        } catch (RuntimeException e) {
            if (!synchronizationActive) {
                deleteUploadedImageSilently(newProfileImageKey);
            }
            throw e;
        }

        if (!synchronizationActive) {
            deletePreviousProfileImageSilently(previousProfileImageKey, newProfileImageKey);
        }

        return ProfileImageUpdateResponse.builder()
                .profileImageUrl(s3Service.getFileUrl(newProfileImageKey))
                .build();
    }

    @Transactional(readOnly = true)
    public ProfileImageResponse getProfileImage(User principal) {
        validateAuthenticated(principal);

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return ProfileImageResponse.builder()
                .profileImageUrl(s3Service.getFileUrl(user.getProfileImageKey()))
                .build();
    }

    private void validateAuthenticated(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }
    }

    private void validateProfileImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_REQUIRED);
        }

        if (image.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED);
        }

        String contentType = image.getContentType();

        if (contentType == null || !ALLOWED_PROFILE_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
        }
    }

    private String uploadProfileImage(MultipartFile image, Long usersId) {
        try {
            return s3Service.upload(
                    image,
                    PROFILE_IMAGE_DIRECTORY + "/" + usersId
            );
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_FAILED);
        }
    }

    private void registerProfileImageCleanup(
            String previousProfileImageKey,
            String newProfileImageKey
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                deletePreviousProfileImageSilently(previousProfileImageKey, newProfileImageKey);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteUploadedImageSilently(newProfileImageKey);
                }
            }
        });
    }

    private void deletePreviousProfileImageSilently(
            String previousProfileImageKey,
            String newProfileImageKey
    ) {
        if (previousProfileImageKey == null
                || previousProfileImageKey.isBlank()
                || previousProfileImageKey.equals(newProfileImageKey)) {
            return;
        }

        try {
            s3Service.delete(previousProfileImageKey);
        } catch (RuntimeException e) {
            log.warn("Failed to delete previous profile image from S3.", e);
        }
    }

    private void deleteUploadedImageSilently(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }

        try {
            s3Service.delete(imageKey);
        } catch (RuntimeException e) {
            log.warn("Failed to delete uploaded profile image from S3 after rollback.", e);
        }
    }
}
