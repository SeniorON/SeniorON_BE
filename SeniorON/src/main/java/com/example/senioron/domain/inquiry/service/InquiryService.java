package com.example.senioron.domain.inquiry.service;

import com.example.senioron.domain.inquiry.dto.request.InquiryCreateRequest;
import com.example.senioron.domain.inquiry.dto.response.InquiryCreateResponse;
import com.example.senioron.domain.inquiry.dto.response.InquiryDetailResponse;
import com.example.senioron.domain.inquiry.dto.response.InquiryListItemResponse;
import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryImage;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.inquiry.repository.InquiryRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryService {

    private static final int MAX_IMAGE_COUNT = 5;

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional
    public InquiryCreateResponse createInquiry(
            User principal,
            InquiryCreateRequest request,
            List<MultipartFile> images
    ) {
        validateRequest(request);

        List<MultipartFile> validImages = normalizeImages(images);

        if (validImages.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INQUIRY_IMAGE_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .status(InquiryStatus.WAITING)
                .build();

        List<String> uploadedImageKeys = uploadImages(user.getUsersId(), validImages);

        for (String imageKey : uploadedImageKeys) {
            InquiryImage image = InquiryImage.builder()
                    .inquiry(inquiry)
                    .imageUrl(imageKey)
                    .build();

            inquiry.addImage(image);
        }

        registerUploadedImageRollbackCleanup(uploadedImageKeys);

        Inquiry savedInquiry = inquiryRepository.saveAndFlush(inquiry);
        List<String> imageUrls = uploadedImageKeys.stream()
                .map(s3Service::getFileUrl)
                .toList();

        return InquiryCreateResponse.from(savedInquiry, imageUrls);
    }

    @Transactional(readOnly = true)
    public List<InquiryListItemResponse> getMyInquiries(User principal) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return inquiryRepository
                .findByUserUsersIdOrderByCreatedAtDescInquiryIdDesc(user.getUsersId())
                .stream()
                .map(InquiryListItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InquiryDetailResponse getMyInquiry(
            User principal,
            Long inquiryId
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Inquiry inquiry = inquiryRepository
                .findByInquiryIdAndUserUsersId(inquiryId, user.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));

        List<String> imageUrls = inquiry.getImages().stream()
                .map(InquiryImage::getImageUrl)
                .map(s3Service::getFileUrl)
                .toList();

        return InquiryDetailResponse.from(inquiry, imageUrls);
    }

    private void validateRequest(InquiryCreateRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.INQUIRY_TITLE_REQUIRED);
        }

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.INQUIRY_CONTENT_REQUIRED);
        }
    }

    private List<MultipartFile> normalizeImages(List<MultipartFile> images) {
        if (images == null) {
            return Collections.emptyList();
        }

        return images.stream()
                .filter(image -> image != null && !image.isEmpty())
                .toList();
    }

    private List<String> uploadImages(
            Long userId,
            List<MultipartFile> images
    ) {
        if (images.isEmpty()) {
            return Collections.emptyList();
        }

        String directory = "inquiries/" + userId;
        List<String> uploadedImageKeys = new ArrayList<>();

        try {
            for (MultipartFile image : images) {
                uploadedImageKeys.add(s3Service.upload(image, directory));
            }
        } catch (RuntimeException exception) {
            deleteUploadedImagesSafely(uploadedImageKeys);
            throw exception;
        }

        return uploadedImageKeys;
    }

    private void registerUploadedImageRollbackCleanup(List<String> uploadedImageKeys) {
        if (uploadedImageKeys.isEmpty()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteUploadedImagesSafely(uploadedImageKeys);
                        }
                    }
                }
        );
    }

    private void deleteUploadedImagesSafely(List<String> imageKeys) {
        for (String imageKey : imageKeys) {
            try {
                s3Service.delete(imageKey);
            } catch (RuntimeException exception) {
                log.error(
                        "문의 등록 실패 후 S3 객체 삭제 실패, imageKey={}",
                        imageKey,
                        exception
                );
            }
        }
    }
}
