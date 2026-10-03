package com.example.senioron.domain.family.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "family-photo.thumbnail-backfill.enabled",
        havingValue = "true"
)
public class FamilyPhotoThumbnailBackfillRunner implements ApplicationRunner {

    private final FamilyPhotoThumbnailBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        backfillService.backfill();
    }
}