package com.example.senioron.domain.home.service;

import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.dto.request.HomeButtonSaveRequest;
import com.example.senioron.domain.home.dto.request.HomeFontSizeUpdateRequest;
import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.home.dto.response.*;
import com.example.senioron.domain.device.dto.response.DeviceDetailResponse;
import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.FontSize;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.entity.HomeSetting;
import com.example.senioron.domain.home.entity.MusicApp;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.home.repository.HomeSettingRepository;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Slf4j
@Service
public class HomeService {

    private static final int MIN_BUTTON_COUNT = 8;
    private static final int MAX_BUTTON_COUNT = 18;
    private static final Set<String> REQUIRED_DEFAULT_BUTTONS = Set.of(
            "MEDICATION",
            "SETTINGS",
            "PHOTO",
            "EMERGENCY"
    );

    private final HomeRepository homeRepository;
    private final ButtonOptionRepository buttonOptionRepository;
    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final HomeSettingRepository homeSettingRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private static final long DEVICE_OFFLINE_THRESHOLD_MINUTES = 11;
    private final HomeWebSocketService homeWebSocketService;

    private Home createHomeButton(
            User user,
            HomeButtonSaveRequest.ButtonRequest buttonRequest
    ) {
        String buttonName = resolveButtonName(
                buttonRequest.getButtonName(),
                null
        );

        String packageName =
                buttonRequest.getPackageName() == null
                        || buttonRequest.getPackageName().isBlank()
                        ? null
                        : buttonRequest.getPackageName().trim();

        String actionValue =
                buttonRequest.getActionValue() == null
                        || buttonRequest.getActionValue().isBlank()
                        ? null
                        : buttonRequest.getActionValue().trim();

        return Home.createButton(
                user,
                buttonRequest.getButtonOrder(),
                buttonName,
                null,
                buttonRequest.getActionType(),
                actionValue,
                packageName
        );
    }

    private List<Home> createInitialHomeButtons(
            User user
    ) {

        return List.of(
                Home.createButton(
                        user,
                        1,
                        "전화",
                        null,
                        ActionType.DEFAULT,
                        "PHONE",
                        null
                ),
                Home.createButton(
                        user,
                        2,
                        "메시지",
                        null,
                        ActionType.DEFAULT,
                        "MESSAGE",
                        null
                ),
                Home.createButton(
                        user,
                        3,
                        "카메라",
                        null,
                        ActionType.DEFAULT,
                        "CAMERA",
                        null
                ),
                Home.createButton(
                        user,
                        4,
                        "사진",
                        null,
                        ActionType.DEFAULT,
                        "PHOTO",
                        null
                ),
                Home.createButton(
                        user,
                        5,
                        "유튜브",
                        null,
                        ActionType.APP,
                        "YOUTUBE",
                        "com.google.android.youtube"
                ),
                Home.createButton(
                        user,
                        6,
                        "설정",
                        null,
                        ActionType.DEFAULT,
                        "SETTINGS",
                        null
                ),
                Home.createButton(
                        user,
                        7,
                        "복약",
                        null,
                        ActionType.DEFAULT,
                        "MEDICATION",
                        null
                ),
                Home.createButton(
                        user,
                        8,
                        "긴급알림",
                        null,
                        ActionType.DEFAULT,
                        "EMERGENCY",
                        null
                ),
                Home.createButton(
                        user,
                        9,
                        "카카오톡",
                        null,
                        ActionType.APP,
                        "KAKAO_TALK",
                        "com.kakao.talk"
                ),
                Home.createButton(
                        user,
                        10,
                        "네이버",
                        null,
                        ActionType.APP,
                        "NAVER",
                        "com.nhn.android.search"
                )
        );
    }

    private List<Home> getOrCreateInitialHomeButtons(User user) {

        // 같은 사용자의 초기 버튼 생성을 동시에 실행하지 못하도록 DB 행 잠금
        User lockedUser = userRepository
                .findByIdForUpdate(user.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(
                        lockedUser
                );

        if (!homes.isEmpty()) {
            return homes;
        }

        List<Home> initialButtons =
                createInitialHomeButtons(lockedUser);

        return homeRepository.saveAll(initialButtons);
    }

    private List<Home> getDisconnectedPreviewButtons(
            User homeOwner
    ) {
        return createInitialHomeButtons(homeOwner);
    }


    private String resolveButtonName(
            String requestedButtonName,
            String defaultButtonName
    ) {

        String resolvedName =
                requestedButtonName == null
                        || requestedButtonName.isBlank()
                        ? defaultButtonName
                        : requestedButtonName;

        if (resolvedName == null
                || resolvedName.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        return resolvedName.trim();
    }

    private boolean isDeviceConnected(
            LocalDateTime lastConnectedAt
    ) {
        if (lastConnectedAt == null) {
            return false;
        }

        LocalDateTime offlineThreshold =
                LocalDateTime.now()
                        .minusMinutes(
                                DEVICE_OFFLINE_THRESHOLD_MINUTES
                        );

        return !lastConnectedAt.isBefore(
                offlineThreshold
        );
    }

    private boolean isDeviceDisconnected(
            Optional<User> seniorUser
    ) {
        if (seniorUser.isEmpty()) {
            return true;
        }

        return deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        seniorUser.get()
                )
                .map(this::isDeviceDisconnected)
                .orElse(true);
    }

    private boolean isDeviceDisconnected(
            Device device
    ) {
        return device.getConnectionStatus() == DeviceStatus.DISCONNECTED
                || !isDeviceConnected(device.getLastConnectedAt());
    }


    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            DeviceRepository deviceRepository,
            SeniorRepository seniorRepository,
            HomeSettingRepository homeSettingRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            RefreshTokenRepository refreshTokenRepository,
            HomeWebSocketService homeWebSocketService
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.deviceRepository = deviceRepository;
        this.seniorRepository = seniorRepository;
        this.homeSettingRepository = homeSettingRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.homeWebSocketService = homeWebSocketService;
    }

    /**
     * 자녀 홈 화면 조회
     */
    @Transactional
    public HomeResponse getHome(Long seniorId) {

        long methodStart = System.nanoTime();
        User currentUser = getCurrentUser();
        log.debug("[TIMING] getCurrentUser : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - methodStart));

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        Senior seniorProfile =
                getManagedSenior(
                        currentUser,
                        seniorId
                );

        User seniorUser =
                seniorProfile.getParentUser();

        long afterDeviceCheck = System.nanoTime();
        Optional<Device> latestDevice =
                seniorUser == null
                        ? Optional.empty()
                        : deviceRepository
                        .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                                seniorUser
                        );

        boolean deviceDisconnected =
                latestDevice
                        .map(this::isDeviceDisconnected)
                        .orElse(true);

        log.debug(
                "[TIMING] deviceDisconnected check : {}ms",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - afterDeviceCheck)
        );

        HomeResponse.ConnectionResponse connection =
                createConnectionResponse(latestDevice);

        List<Home> homes =
                deviceDisconnected
                        ? getDisconnectedPreviewButtons(currentUser)
                        : getOrCreateInitialHomeButtons(seniorUser);

        List<HomeResponse.HomeButtonResponse> buttons =
                homes.stream()
                        .map(home ->
                                new HomeResponse.HomeButtonResponse(
                                        home.getHomeId(),
                                        home.getButtonOrder(),
                                        home.getButtonName(),
                                        home.getIcon(),
                                        home.getActionType(),
                                        home.getActionValue(),
                                        home.getPackageName()
                                )
                        )
                        .toList();

        HomeResponse.SeniorProfileResponse seniorProfileResponse =
                createSeniorProfileResponse(
                        seniorProfile,
                        seniorProfile.getRelation() == null
                                ? null
                                : seniorProfile.getRelation().name()
                );

        FontSize fontSize =
                deviceDisconnected
                        ? FontSize.MEDIUM
                        : getFontSize(seniorUser);

        HomeResponse.MusicCardResponse musicCard =
                deviceDisconnected
                        ? HomeResponse.MusicCardResponse.empty()
                        : getMusicCardResponse(seniorUser);

        TodayScheduleResponse todaySchedule =
                deviceDisconnected || seniorUser == null
                        ? null
                        : getTodayHospitalSchedule(
                        seniorUser
                );

        log.debug("[TIMING] getHome total : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - methodStart));
        return new HomeResponse(
                currentUser.getName(),
                connection,
                seniorProfileResponse,
                fontSize,
                musicCard,
                todaySchedule,
                buttons
        );
    }

    /**
     * 시니어 프로필 최초 등록 및 수정
     */
    @Transactional
    public SeniorProfileUpdateResponse updateSeniorProfile(
            Long seniorId,
            SeniorProfileUpdateRequest request
    ) {
        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        String resolvedCustomRelation =
                resolveCustomRelation(
                        request.relation(),
                        request.customRelation()
                );

        String normalizedPhoneNumber =
                normalizePhoneNumber(
                        request.phoneNumber()
                );

        Senior senior =
                getManagedSenior(
                        currentUser,
                        seniorId
                );

        senior.updateProfile(
                request.name(),
                request.relation(),
                resolvedCustomRelation,
                request.birth(),
                normalizedPhoneNumber,
                request.address(),
                request.detailAddress(),
                request.latitude(),
                request.longitude()
        );

        return SeniorProfileUpdateResponse.from(senior);
    }

    /**
     * 홈 버튼과 노래 카드 전체 저장
     */
    @Transactional
    public void saveButtons(
            Long seniorId,
            HomeButtonSaveRequest request
    ) {

        long totalStart = System.nanoTime();

        User user = getCurrentUser();

        Senior senior =
                getManagedSenior(user, seniorId);

        validatePrimaryManager(
                user,
                senior
        );

        User seniorUser =
                getSeniorUser(
                        senior
                );

        validateSeniorDeviceConnected(
                seniorUser
        );

        List<HomeButtonSaveRequest.ButtonRequest> buttonRequests =
                request.getButtons();

        if (buttonRequests == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        if (buttonRequests.size() < MIN_BUTTON_COUNT) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_MINIMUM_NOT_MET
            );
        }

        if (buttonRequests.size() > MAX_BUTTON_COUNT) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_LIMIT_EXCEEDED
            );
        }

        long start = System.nanoTime();

        validateSaveButtonRequests(
                buttonRequests
        );

        log.debug("validateSaveButtonRequests : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

        start = System.nanoTime();

        homeRepository.deleteAllByUser(
                seniorUser
        );
        homeRepository.flush();

        log.debug("deleteAllByUser : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

        start = System.nanoTime();

        List<Home> newHomes =
                buttonRequests.stream()
                        .map(buttonRequest ->
                                createHomeButton(
                                        seniorUser,
                                        buttonRequest
                                )
                        )
                        .toList();

        log.debug("createHomeButton : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

        start = System.nanoTime();

        homeRepository.saveAllAndFlush(
                newHomes
        );

        log.debug("saveAll : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

        start = System.nanoTime();

        HomeSetting homeSetting =
                homeSettingRepository
                        .findByUser(seniorUser)
                        .orElseGet(() ->
                                HomeSetting.builder()
                                        .user(seniorUser)
                                        .fontSize(FontSize.MEDIUM)
                                        .build()
                        );

        homeSetting.updateMusicApp(
                request.getMusicApp()
        );

        homeSettingRepository.save(
                homeSetting
        );

        homeWebSocketService.notifyHomeUpdated(
                seniorUser.getUsersId()
        );

        log.debug("homeSetting.save : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

        log.debug("saveButtons TOTAL : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStart));
    }

    /**
     * 추가 가능한 홈 버튼 옵션 조회
     */
    @Transactional(readOnly = true)
    public List<ButtonOptionResponse> getButtonOptions(
            Long seniorId
    ) {

        User user = getCurrentUser();

        Senior senior =
                getManagedSenior(user, seniorId);

        validatePrimaryManager(
                user,
                senior
        );

        User seniorUser =
                getSeniorUser(
                        senior
                );

        validateSeniorDeviceConnected(
                seniorUser
        );

        return buttonOptionRepository
                .findAll()
                .stream()
                .map(option ->
                        new ButtonOptionResponse(
                                option.getOptionId(),
                                option.getButtonName(),
                                option.getIcon(),
                                option.getActionType(),
                                option.getActionValue()
                        )
                )
                .toList();
    }

    /**
     * 시니어 홈 화면 조회
     */
    @Transactional
    public SeniorHomeResponse getSeniorHome() {

        User parent = getCurrentUser();

        if (parent.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_HOME_ACCESS_DENIED
            );
        }

        boolean deviceDisconnected =
                deviceRepository
                        .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                                parent
                        )
                        .map(this::isDeviceDisconnected)
                        .orElse(true);

        List<Home> homes =
                deviceDisconnected
                        ? createInitialHomeButtons(parent)
                        : getOrCreateInitialHomeButtons(parent);

        List<SeniorHomeResponse.ButtonResponse> buttons =
                homes.stream()
                        .map(home ->
                                new SeniorHomeResponse.ButtonResponse(
                                        home.getHomeId(),
                                        home.getButtonOrder(),
                                        home.getButtonName(),
                                        home.getIcon(),
                                        home.getActionType(),
                                        home.getActionValue(),
                                        home.getPackageName()
                                )
                        )
                        .toList();

        FontSize fontSize =
                deviceDisconnected
                        ? FontSize.MEDIUM
                        : getFontSize(parent);

        TodayScheduleResponse todaySchedule =
                deviceDisconnected
                        ? TodayScheduleResponse.empty()
                        : getTodayHospitalSchedule(parent);

        HomeResponse.MusicCardResponse musicCard =
                deviceDisconnected
                        ? HomeResponse.MusicCardResponse.empty()
                        : getMusicCardResponse(parent);

        return new SeniorHomeResponse(
                fontSize,
                musicCard,
                todaySchedule,
                buttons
        );
    }

    /**
     * 시니어 기기 연결 상태 상세 조회
     */
    @Transactional(readOnly = true)
    public DeviceDetailResponse getDeviceDetail(Long seniorId) {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        Senior senior = getManagedSenior(currentUser, seniorId);

        User seniorUser = senior.getParentUser();

        if (seniorUser == null) {
            return DeviceDetailResponse.disconnected();
        }

        return deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        seniorUser
                )
                .map(device -> {

                    if (device.getConnectionStatus()
                            == DeviceStatus.DISCONNECTED) {

                        return DeviceDetailResponse.disconnected();
                    }

                    boolean loginExpired =
                            refreshTokenRepository
                                    .findByUserAndDeviceIdentifier(
                                            seniorUser,
                                            device.getDeviceIdentifier()
                                    )
                                    .map(refreshToken ->
                                            refreshToken.isExpired(LocalDateTime.now())
                                    )
                                    .orElse(true);

                    if (loginExpired) {
                        return new DeviceDetailResponse(
                                device.getDeviceName(),
                                false,
                                DeviceStatus.LOGIN_EXPIRED,
                                null,   // batteryLevel
                                null,   // charging
                                null,   // deviceStatusSharingEnabled
                                null,   // networkConnected
                                null,   // defaultHomeEnabled
                                null,   // locationPermissionGranted
                                null,   // gpsEnabled
                                null,   // notificationPermissionGranted
                                null,   // appExecutionMaintained
                                device.getLastConnectedAt(),
                                device.getLastLocationUpdatedAt()
                        );
                    }

                    boolean connected =
                            isDeviceConnected(
                                    device.getLastConnectedAt()
                            );

                    DeviceStatus currentStatus =
                            connected
                                    ? DeviceStatus.ONLINE
                                    : DeviceStatus.OFFLINE;

                    return new DeviceDetailResponse(
                            device.getDeviceName(),
                            connected,
                            currentStatus,
                            device.getBatteryLevel(),
                            device.getCharging(),
                            device.getDeviceStatusSharingEnabled(),
                            device.getNetworkConnected(),
                            device.getDefaultHomeEnabled(),
                            device.getLocationPermissionGranted(),
                            device.getGpsEnabled(),
                            device.getNotificationPermissionGranted(),
                            device.getAppExecutionMaintained(),
                            device.getLastConnectedAt(),
                            device.getLastLocationUpdatedAt()
                    );
                })
                .orElseGet(
                        DeviceDetailResponse::disconnected
                );
    }

    /**
     * 오늘 병원 일정 상세 목록 조회
     */
    @Transactional(readOnly = true)
    public List<TodayHospitalListResponse> getTodayHospitalSchedules(
            Long seniorId
    ) {

        long totalStart = System.nanoTime();

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        Senior senior =
                getManagedSenior(
                        currentUser,
                        seniorId
                );

        User seniorUser =
                getSeniorUser(
                        senior
                );

        long start = System.nanoTime();

        List<Hospital> hospitals =
                findTodayHospitalSchedules(
                        seniorUser
                );

        log.debug(
                "findTodayHospitalSchedules : {}ms",
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start
                )
        );

        start = System.nanoTime();

        List<TodayHospitalListResponse> result =
                hospitals.stream()
                        .map(TodayHospitalListResponse::from)
                        .toList();

        log.debug(
                "mapping Response : {}ms",
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start
                )
        );

        log.debug(
                "getTodayHospitalSchedules TOTAL : {}ms",
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - totalStart
                )
        );

        return result;
    }

    /**
     * 홈 화면 오늘 병원 일정 요약 조회
     */
    private TodayScheduleResponse getTodayHospitalSchedule(
            User currentUser
    ) {

        List<Hospital> hospitals =
                findTodayHospitalSchedules(
                        currentUser
                );

        int scheduleCount =
                hospitals.size();

        if (scheduleCount == 0) {
            return TodayScheduleResponse.empty();
        }

        if (scheduleCount == 1) {

            Hospital hospital =
                    hospitals.get(0);

            return TodayScheduleResponse.detail(
                    hospital.getHospital_id(),
                    hospital.getHospitalName(),
                    hospital.getDepartment(),
                    hospital.getScheduleTime()
            );
        }

        return TodayScheduleResponse.count(
                scheduleCount
        );
    }

    /**
     * 가족 내 주담당자와 보조담당자가 등록한
     * 오늘 병원 일정을 시간순으로 조회
     */
    private List<Hospital> findTodayHospitalSchedules(
            User parent
    ) {

        if (parent.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        LocalDate today =
                LocalDate.now();

        return hospitalRepository
                .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                        parent,
                        today,
                        today
                );
    }

    /**
     * 홈 글씨 크기 수정
     */
    @Transactional
    public void updateFontSize(
            Long seniorId,
            HomeFontSizeUpdateRequest request
    ) {

        long totalStart = System.nanoTime();

        User user = getCurrentUser();

        Senior senior =
                getManagedSenior(user, seniorId);

        validatePrimaryManager(
                user,
                senior
        );

        User seniorUser =
                getSeniorUser(senior);

        validateSeniorDeviceConnected(
                seniorUser
        );

        long start = System.nanoTime();

        HomeSetting homeSetting =
                homeSettingRepository
                        .findByUser(seniorUser)
                        .orElseGet(() ->
                                HomeSetting.builder()
                                        .user(seniorUser)
                                        .fontSize(FontSize.MEDIUM)
                                        .build()
                        );

        System.out.println("findHomeSetting : "
                + TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
        ) + "ms");

        start = System.nanoTime();

        homeSetting.updateFontSize(
                request.getFontSize()
        );

        homeSettingRepository.save(
                homeSetting
        );

        homeWebSocketService.notifyHomeUpdated(
                seniorUser.getUsersId()
        );

        System.out.println("saveHomeSetting : "
                + TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
        ) + "ms");

        System.out.println("updateFontSize TOTAL (Service 내부) : "
                + TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - totalStart
        ) + "ms");
    }

    private void validateSaveButtonRequests(
            List<HomeButtonSaveRequest.ButtonRequest> buttonRequests
    ) {

        boolean hasInvalidActionInformation =
                buttonRequests.stream()
                        .anyMatch(buttonRequest -> {

                            ActionType actionType =
                                    buttonRequest.getActionType();

                            String actionValue =
                                    buttonRequest.getActionValue();

                            String packageName =
                                    buttonRequest.getPackageName();

                            if (actionType == null) {
                                return true;
                            }

                            if (actionType == ActionType.DEFAULT) {
                                return actionValue == null
                                        || actionValue.isBlank()
                                        || (packageName != null
                                        && !packageName.isBlank());
                            }

                            if (actionType == ActionType.APP) {
                                return packageName == null
                                        || packageName.isBlank();
                            }

                            return true;
                        });

        if (hasInvalidActionInformation) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        List<String> packageNames =
                buttonRequests.stream()
                        .map(
                                HomeButtonSaveRequest.ButtonRequest
                                        ::getPackageName
                        )
                        .filter(packageName ->
                                packageName != null
                                        && !packageName.isBlank()
                        )
                        .map(String::trim)
                        .toList();

        if (packageNames.stream().distinct().count()
                != packageNames.size()) {

            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        boolean hasNullButtonOrder =
                buttonRequests.stream()
                        .anyMatch(buttonRequest ->
                                buttonRequest.getButtonOrder() == null
                        );

        if (hasNullButtonOrder) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_ORDER
            );
        }

        long buttonOrderCount =
                buttonRequests.stream()
                        .map(
                                HomeButtonSaveRequest.ButtonRequest
                                        ::getButtonOrder
                        )
                        .distinct()
                        .count();

        if (buttonOrderCount != buttonRequests.size()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ORDER
            );
        }

        List<Integer> sortedOrders =
                buttonRequests.stream()
                        .map(
                                HomeButtonSaveRequest.ButtonRequest
                                        ::getButtonOrder
                        )
                        .sorted()
                        .toList();

        validateSequentialOrders(sortedOrders);

        Set<String> defaultButtons =
                buttonRequests.stream()
                        .filter(buttonRequest ->
                                buttonRequest.getActionType() == ActionType.DEFAULT
                        )
                        .map(buttonRequest ->
                                buttonRequest.getActionValue().trim()
                        )
                        .collect(Collectors.toSet());

        if (!defaultButtons.containsAll(REQUIRED_DEFAULT_BUTTONS)) {
            throw new BusinessException(
                    ErrorCode.REQUIRED_HOME_BUTTON_MISSING
            );
        }
    }

    private void validateSequentialOrders(
            List<Integer> sortedOrders
    ) {

        for (int i = 0; i < sortedOrders.size(); i++) {

            if (!sortedOrders.get(i).equals(i + 1)) {
                throw new BusinessException(
                        ErrorCode.INVALID_HOME_BUTTON_ORDER
                );
            }
        }
    }

    private FontSize getFontSize(
            User homeOwner
    ) {

        return homeSettingRepository
                .findByUser(homeOwner)
                .map(HomeSetting::getFontSize)
                .orElse(FontSize.MEDIUM);
    }

    private HomeResponse.MusicCardResponse getMusicCardResponse(
            User homeOwner
    ) {

        return homeSettingRepository
                .findByUser(homeOwner)
                .map(HomeSetting::getMusicApp)
                .map(this::createMusicCardResponse)
                .orElseGet(
                        HomeResponse.MusicCardResponse::empty
                );
    }

    private HomeResponse.MusicCardResponse createMusicCardResponse(
            MusicApp musicApp
    ) {

        if (musicApp == null) {
            return HomeResponse
                    .MusicCardResponse
                    .empty();
        }

        return new HomeResponse.MusicCardResponse(
                true,
                musicApp,
                musicApp.getDisplayName(),
                musicApp.getIcon(),
                ActionType.APP,
                musicApp.name().toLowerCase(),
                musicApp.getPackageName()
        );
    }

    private HomeResponse.ConnectionResponse createConnectionResponse(
            Optional<Device> latestDevice
    ) {

        if (latestDevice.isEmpty()) {
            return HomeResponse
                    .ConnectionResponse
                    .disconnected();
        }

        Device device = latestDevice.get();

        if (device.getConnectionStatus()
                == DeviceStatus.DISCONNECTED) {

            return HomeResponse
                    .ConnectionResponse
                    .disconnected();
        }

        boolean connected =
                isDeviceConnected(
                        device.getLastConnectedAt()
                );

        DeviceStatus currentStatus =
                connected
                        ? DeviceStatus.ONLINE
                        : DeviceStatus.OFFLINE;

        return new HomeResponse.ConnectionResponse(
                device.getDeviceName(),
                connected,
                currentStatus,
                device.getBatteryLevel()
        );
    }

    private HomeResponse.SeniorProfileResponse
    createSeniorProfileResponse(
            Senior senior,
            String relation
    ) {

        LocalDate birth =
                senior.getBirth();

        Integer age =
                birth == null
                        ? null
                        : Period.between(
                        birth,
                        LocalDate.now()
                ).getYears();

        return new HomeResponse.SeniorProfileResponse(
                senior.getSeniorId(),
                senior.getName(),
                relation,
                birth,
                age,
                senior.getAddress(),
                senior.getDetailAddress(),
                senior.getPhoneNumber()
        );
    }

    private String resolveCustomRelation(
            SeniorRelation relation,
            String customRelation
    ) {

        if (relation != SeniorRelation.OTHER) {
            return null;
        }

        if (customRelation == null
                || customRelation.isBlank()) {

            throw new BusinessException(
                    ErrorCode.CUSTOM_RELATION_REQUIRED
            );
        }

        return customRelation.trim();
    }

    private String normalizePhoneNumber(
            String phoneNumber
    ) {

        return phoneNumber
                .replace("-", "")
                .replace(" ", "");
    }

    private Senior saveSeniorOrThrowAlreadyExists(
            Senior senior
    ) {

        try {
            return seniorRepository.saveAndFlush(
                    senior
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.SENIOR_ALREADY_EXISTS
            );
        }
    }

    private FamilyMember getFamilyMember(
            User user,
            Senior senior
    ) {

        if (senior.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        return familyMemberRepository
                .findByUserAndFamily(
                        user,
                        senior.getFamily()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.FORBIDDEN
                        )
                );
    }

    private void validateSeniorDeviceConnected(
            User seniorUser
    ) {

        boolean disconnected =
                deviceRepository
                        .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                                seniorUser
                        )
                        .map(this::isDeviceDisconnected)
                        .orElse(true);

        if (disconnected) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }
    }

    private void validatePrimaryManager(
            User user,
            Senior senior
    ) {

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.HOME_SETTING_ACCESS_DENIED
            );
        }

        FamilyMember familyMember =
                getFamilyMember(user, senior);

        if (familyMember.getManagerType()
                != ManagerType.PRIMARY) {

            throw new BusinessException(
                    ErrorCode.HOME_SETTING_ACCESS_DENIED
            );
        }
    }

    private User getSeniorUser(
            Senior senior
    ) {

        User seniorUser = senior.getParentUser();

        if (seniorUser == null) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        return seniorUser;
    }

    private Senior getManagedSenior(
            User child,
            Long seniorId
    ) {

        if (child.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        Senior senior = seniorRepository
                .findById(seniorId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.SENIOR_NOT_FOUND
                        )
                );

        getFamilyMember(child, senior);

        return senior;
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return (User) authentication
                .getPrincipal();
    }
}
