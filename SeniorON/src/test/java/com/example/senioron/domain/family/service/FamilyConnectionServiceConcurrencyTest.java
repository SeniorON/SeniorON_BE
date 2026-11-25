package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@Import({FamilyConnectionService.class, UnusedFamilyCleanupService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FamilyConnectionServiceConcurrencyTest {

    @Autowired private FamilyConnectionService service;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void disconnectWaitsForExistingFamilyWriteLockBeforeCleanup()
            throws Exception {
        Family family = familyRepository.saveAndFlush(Family.builder()
                .seniorCode("LOCK-TEST")
                .build());
        User primary = userRepository.saveAndFlush(User.builder()
                .loginId("lock-primary")
                .name("주 담당자")
                .role(Role.CHILD)
                .build());
        familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(primary)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .build());

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try {
            Future<?> lockHolder = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        familyRepository.findByIdForUpdate(family.getFamilyId())
                                .orElseThrow();
                        lockAcquired.countDown();
                        await(releaseLock);
                    })
            );
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> disconnect = executor.submit(() ->
                    service.disconnect(primary, family.getFamilyId())
            );

            assertThatThrownBy(() -> disconnect.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLock.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            disconnect.get(5, TimeUnit.SECONDS);

            assertThat(familyRepository.findById(family.getFamilyId())).isEmpty();
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("가족 잠금 해제 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("가족 잠금 대기가 중단되었습니다.", e);
        }
    }
}
