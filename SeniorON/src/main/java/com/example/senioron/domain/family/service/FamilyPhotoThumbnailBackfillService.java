package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPhotoThumbnailBackfillService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoPersistenceService persistenceService;
    private final S3Service s3Service;
    private final ThumbnailService thumbnailService;

    public int backfill() {
        long afterId = 0L;
        int generatedCount = 0;

        while (true) {
            List<FamilyPhoto> photos =
                    familyPhotoRepository.findPhotosWithoutThumbnail(
                            afterId,
                            PageRequest.of(0, 100)
                    );

            if (photos.isEmpty()) {
                break;
            }

            for (FamilyPhoto photo : photos) {
                afterId = photo.getFamilyPhotoId();

                if (generateThumbnail(photo)) {
                    generatedCount++;
                }
            }
        }

        log.info("기존 가족사진 썸네일 생성 완료, 생성 수={}", generatedCount);
        return generatedCount;
    }

    private boolean generateThumbnail(FamilyPhoto photo) {
        String thumbnailKey = null;

        try {
            byte[] originalBytes = s3Service.download(photo.getImageKey());
            byte[] thumbnailBytes = thumbnailService.create(originalBytes);
            thumbnailKey = s3Service.uploadThumbnail(thumbnailBytes);

            if (persistenceService.attachThumbnail(
                    photo.getFamilyPhotoId(),
                    thumbnailKey
            )) {
                return true;
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "기존 가족사진 썸네일 생성 실패, photoId={}",
                    photo.getFamilyPhotoId(),
                    exception
            );
        }

        // DB에 연결되지 못한 새 썸네일 정리
        if (thumbnailKey != null) {
            try {
                s3Service.delete(thumbnailKey);
            } catch (RuntimeException exception) {
                log.warn(
                        "기존 가족사진 썸네일 정리 실패, thumbnailKey={}",
                        thumbnailKey,
                        exception
                );
            }
        }

        return false;
    }
}