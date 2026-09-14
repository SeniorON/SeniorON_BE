package com.example.senioron.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class HomeServiceInitialButtonsConcurrencyTest {

    @Autowired
    private HomeService homeService;

    @Autowired
    private HomeRepository homeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Test
    void concurrentFirstHomeRequestsCreateInitialButtonsOnlyOnce()
            throws Exception {

        /*
         * Given
         *
         * 가족 생성
         */
        Family family = familyRepository.saveAndFlush(
                Family.builder()
                        .familyCode("CONCURRENCY-TEST-FAMILY")
                        .build()
        );

        /*
         * 홈 설정을 소유할 자녀 계정
         */
        User child = userRepository.saveAndFlush(
                User.builder()
                        .loginId("concurrency-child")
                        .email("concurrency-child@test.com")
                        .password("test")
                        .name("테스트 자녀")
                        .role(Role.CHILD)
                        .build()
        );
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(child)
                        .family(family)
                        .managerType(ManagerType.PRIMARY)
                        .build()
        );
        child.updateFamily(family);
        child.updateManagerType(ManagerType.PRIMARY);

        /*
         * 연결된 시니어 계정
         *
         * getHome()에서 기기가 연결되어 있어야
         * getOrCreateInitialHomeButtons()를 실행하기 때문에 필요하다.
         */
        User senior = userRepository.saveAndFlush(
                User.builder()
                        .loginId("concurrency-senior")
                        .email("concurrency-senior@test.com")
                        .password("test")
                        .name("테스트 시니어")
                        .role(Role.PARENT)
                        .build()
        );
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(senior)
                        .family(family)
                        .managerType(ManagerType.NONE)
                        .build()
        );
        senior.updateFamily(family);
        senior.updateManagerType(ManagerType.NONE);
        Senior seniorProfile = seniorRepository.saveAndFlush(
                Senior.builder()
                        .name("테스트 시니어")
                        .birth(LocalDate.of(1950, 1, 1))
                        .phoneNumber("01012345678")
                        .family(family)
                        .registeredBy(child)
                        .parentUser(senior)
                        .build()
        );

        Long seniorId = seniorProfile.getSeniorId();

        /*
         * 연결된 시니어 기기 생성
         */
        deviceRepository.saveAndFlush(
                Device.builder()
                        .user(senior)
                        .deviceIdentifier("concurrency-test-device")
                        .deviceToken("test-token")
                        .deviceName("테스트 기기")
                        .connectionStatus(DeviceStatus.ONLINE)
                        .batteryLevel(100)
                        .lastConnectedAt(LocalDateTime.now())
                        .build()
        );

        /*
         * 테스트 시작 전에는 홈 버튼이 하나도 없어야 함
         */
        assertThat(
                homeRepository.findAllByUserOrderByButtonOrderAsc(senior)
        ).isEmpty();


        /*
         * When
         *
         * 두 스레드가 준비될 때까지 기다렸다가
         * 동시에 getHome()을 실행한다.
         */
        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        try {

            Future<?> firstRequest =
                    executor.submit(() -> {

                        readyLatch.countDown();

                        try {
                            startLatch.await();

                            setAuthentication(child);

                            homeService.getHome(seniorId);

                        } catch (InterruptedException e) {

                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);

                        } finally {

                            SecurityContextHolder.clearContext();
                        }
                    });

            Future<?> secondRequest =
                    executor.submit(() -> {

                        readyLatch.countDown();

                        try {
                            startLatch.await();

                            setAuthentication(child);

                            homeService.getHome(seniorId);

                        } catch (InterruptedException e) {

                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);

                        } finally {

                            SecurityContextHolder.clearContext();
                        }
                    });

            /*
             * 두 스레드 모두 실행 준비가 끝난 뒤 동시에 시작
             */
            assertThat(
                    readyLatch.await(5, TimeUnit.SECONDS)
            ).isTrue();

            startLatch.countDown();

            /*
             * 두 요청에서 예외가 발생했다면
             * Future.get()에서 테스트가 실패한다.
             */
            firstRequest.get(10, TimeUnit.SECONDS);
            secondRequest.get(10, TimeUnit.SECONDS);

        } finally {

            executor.shutdownNow();
        }


        /*
         * Then
         *
         * 동시에 최초 홈 조회가 2번 발생했지만
         * DB에는 초기 버튼 10개만 존재해야 한다.
         */
        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(
                        senior
                );

        assertThat(homes)
                .hasSize(10);

        /*
         * 버튼 순서도 중복 없이
         * 1 ~ 10까지 한 번씩만 존재하는지 검증
         */
        assertThat(
                homes.stream()
                        .map(Home::getButtonOrder)
                        .toList()
        ).containsExactly(
                1, 2, 3, 4, 5,
                6, 7, 8, 9, 10
        );

        assertThat(
                homes.stream()
                        .map(Home::getButtonOrder)
                        .distinct()
                        .count()
        ).isEqualTo(10);
    }

    private void setAuthentication(User user) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }
}
