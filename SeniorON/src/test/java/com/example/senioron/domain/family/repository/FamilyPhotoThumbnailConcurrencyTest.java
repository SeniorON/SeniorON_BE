package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(20)
class FamilyPhotoThumbnailConcurrencyTest {

    @Autowired private FamilyPhotoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    @BeforeEach
    void createCommittedFixture() {
        fixture = transaction().execute(status -> {
            String unique = UUID.randomUUID().toString();
            Family family = Family.builder().seniorCode(unique.substring(0, 8)).build();
            User user = User.builder().loginId(unique).email(unique + "@example.com")
                    .name("동시 처리 테스트").role(Role.CHILD).build();
            User outsider = User.builder().loginId("other-" + unique)
                    .email("other-" + unique + "@example.com")
                    .name("다른 가족 사용자").role(Role.CHILD).build();
            PhotoGroup group = PhotoGroup.builder().name("동시 처리 사진 그룹").build();
            entityManager.persist(family);
            entityManager.persist(user);
            entityManager.persist(outsider);
            entityManager.persist(group);
            entityManager.persist(FamilyMember.builder().user(user).family(family)
                    .managerType(ManagerType.SUB).build());
            entityManager.persist(PhotoGroupFamily.builder().family(family).photoGroup(group).build());
            FamilyPhoto photo = FamilyPhoto.builder().user(user).photoGroup(group)
                    .imageKey("original/" + unique + ".jpg").build();
            entityManager.persist(photo);
            entityManager.persist(FamilyPhotoGroup.builder().familyPhoto(photo).photoGroup(group).build());
            entityManager.flush();
            return new Fixture(photo.getFamilyPhotoId(), user.getUsersId(),
                    outsider.getUsersId(), group.getId(), family.getFamilyId());
        });
    }

    @AfterEach
    void removeCommittedFixture() {
        if (fixture == null) {
            return;
        }
        transaction().executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM FamilyPhotoGroup p WHERE p.familyPhoto.familyPhotoId = :id")
                    .setParameter("id", fixture.photoId()).executeUpdate();
            repository.deleteById(fixture.photoId());
            repository.flush();
            entityManager.createQuery("DELETE FROM PhotoGroupFamily p WHERE p.photoGroup.id = :id")
                    .setParameter("id", fixture.groupId()).executeUpdate();
            entityManager.createQuery("DELETE FROM FamilyMember m WHERE m.family.familyId = :id")
                    .setParameter("id", fixture.familyId()).executeUpdate();
            entityManager.remove(entityManager.find(PhotoGroup.class, fixture.groupId()));
            entityManager.remove(entityManager.find(User.class, fixture.userId()));
            entityManager.remove(entityManager.find(User.class, fixture.outsiderId()));
            entityManager.remove(entityManager.find(Family.class, fixture.familyId()));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void serializesDeletionAndThumbnailAttachmentInBothOrders(boolean deletionFirst) throws Exception {
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String thumbnailKey = "thumbnails/concurrent-photo.jpg";
        try {
            Future<?> holder = executor.submit(() -> transaction().executeWithoutResult(status -> {
                FamilyPhoto lockedPhoto = null;
                if (deletionFirst) {
                    lockedPhoto = findForDeletion(fixture.userId());
                    assertThat(lockedPhoto.getThumbnailKey()).isNull();
                } else {
                    assertThat(repository.updateThumbnailIfAbsent(fixture.photoId(), thumbnailKey))
                            .isEqualTo(1);
                }
                holderReady.countDown();
                await(releaseHolder);
                if (deletionFirst) {
                    deleteLockedPhoto(lockedPhoto);
                }
            }));
            assertThat(holderReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Object> contender = executor.submit(() -> transaction().execute(status -> {
                contenderStarted.countDown();
                if (deletionFirst) {
                    return repository.updateThumbnailIfAbsent(fixture.photoId(), thumbnailKey);
                }
                FamilyPhoto lockedPhoto = findForDeletion(fixture.userId());
                String observedThumbnail = lockedPhoto.getThumbnailKey();
                deleteLockedPhoto(lockedPhoto);
                return observedThumbnail;
            }));
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> contender.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            holder.get(5, TimeUnit.SECONDS);
            Object result = contender.get(5, TimeUnit.SECONDS);
            if (deletionFirst) {
                // 대상이 삭제됐으므로 일괄 처리에서 새 썸네일을 정리할 수 있다.
                assertThat(result).isEqualTo(0);
            } else {
                // 삭제가 최신 키를 읽어 afterCommit 정리 대상에 포함할 수 있다.
                assertThat(result).isEqualTo(thumbnailKey);
            }
            assertThat(repository.existsById(fixture.photoId())).isFalse();
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void deletionLookupStillRejectsUserOutsideSharedFamily() {
        transaction().executeWithoutResult(status -> {
            assertThat(repository.findAccessibleForDeletionByFamilyPhotoIdAndUser(
                    fixture.photoId(), entityManager.getReference(User.class, fixture.outsiderId())
            )).isEmpty();
            assertThat(repository.existsById(fixture.photoId())).isTrue();
        });
    }

    private FamilyPhoto findForDeletion(Long userId) {
        return repository.findAccessibleForDeletionByFamilyPhotoIdAndUser(
                fixture.photoId(), entityManager.getReference(User.class, userId)
        ).orElseThrow();
    }

    private void deleteLockedPhoto(FamilyPhoto photo) {
        // H2의 JPA 생성 스키마에는 운영 migration의 ON DELETE CASCADE가 없다.
        entityManager.createQuery("DELETE FROM FamilyPhotoGroup p WHERE p.familyPhoto.familyPhotoId = :id")
                .setParameter("id", fixture.photoId()).executeUpdate();
        repository.delete(photo);
        repository.flush();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(Long photoId, Long userId, Long outsiderId, Long groupId, Long familyId) {
    }
}
