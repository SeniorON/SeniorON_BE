package com.example.senioron.domain.family.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class FamilyPhotoThumbnailBackfillRunnerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "true"})
    void runsOnlyWhenExplicitlyEnabled(String enabled) {
        FamilyPhotoThumbnailBackfillService service =
                mock(FamilyPhotoThumbnailBackfillService.class);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(FamilyPhotoThumbnailBackfillService.class, () -> service)
                .withUserConfiguration(FamilyPhotoThumbnailBackfillRunner.class);
        if (enabled != null) {
            runner = runner.withPropertyValues(
                    "family-photo.thumbnail-backfill.enabled=" + enabled
            );
        }

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            if ("true".equals(enabled)) {
                assertThat(context).hasSingleBean(FamilyPhotoThumbnailBackfillRunner.class);
                context.getBean(FamilyPhotoThumbnailBackfillRunner.class)
                        .run(mock(ApplicationArguments.class));
                verify(service).backfill();
                verifyNoMoreInteractions(service);
            } else {
                assertThat(context).doesNotHaveBean(FamilyPhotoThumbnailBackfillRunner.class);
                verifyNoInteractions(service);
            }
        });
    }
}
