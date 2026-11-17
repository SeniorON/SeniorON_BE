package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.event.FamilyPhotoDeletedEvent;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.storage.S3DeletionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyPhotoWithdrawalService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;
    private final FamilyPhotoViewRepository familyPhotoViewRepository;
    private final FamilyPhotoReportRepository familyPhotoReportRepository;
    private final FamilyUserReportRepository familyUserReportRepository;
    private final FamilyPhotoPushTargetService familyPhotoPushTargetService;
    private final ApplicationEventPublisher eventPublisher;
    private final S3DeletionService s3DeletionService;

    // UserService가 사용자 행을 잠근 뒤 호출하며, 탈퇴와 같은 트랜잭션에서 처리한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUploadedPhotos(User user) {
        List<FamilyPhoto> photos =
                familyPhotoRepository.findAllByUploaderForUpdate(user);
        Set<String> objectKeys = new LinkedHashSet<>();

        for (FamilyPhoto photo : photos) {
            List<Long> seniorIds =
                    familyPhotoSeniorRepository.findSeniorIdsByFamilyPhotoId(
                            photo.getFamilyPhotoId()
                    );
            List<String> tokens = familyPhotoPushTargetService.loadDeletionTokens(
                    photo.getFamilyPhotoId(), seniorIds
            );

            objectKeys.add(photo.getImageKey());
            objectKeys.add(photo.getThumbnailKey());

            familyPhotoViewRepository.deleteAllByFamilyPhoto(photo);
            familyPhotoSeniorRepository.deleteAllByFamilyPhoto(photo);
            familyPhotoRepository.delete(photo);

            if (!tokens.isEmpty()) {
                eventPublisher.publishEvent(
                        new FamilyPhotoDeletedEvent(photo.getFamilyPhotoId(), tokens)
                );
            }
        }

        // 일반 삭제에서 신고 검토용으로 보존했던 원본도 탈퇴 시에는 삭제한다.
        objectKeys.addAll(familyPhotoReportRepository.findImageKeysByReportedUser(user));
        objectKeys.addAll(familyUserReportRepository.findImageKeysByReportedUser(user));

        if (!objectKeys.isEmpty()) {
            s3DeletionService.enqueue(objectKeys);
        }
    }

}
