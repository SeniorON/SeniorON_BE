package com.example.senioron.domain.home.service;

import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.dto.request.HomeButtonCreateRequest;
import com.example.senioron.domain.home.dto.request.HomeButtonSaveRequest;
import com.example.senioron.domain.home.dto.request.HomeButtonUpdateRequest;
import com.example.senioron.domain.home.dto.request.HomeFontSizeUpdateRequest;
import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.home.dto.response.*;
import com.example.senioron.domain.device.dto.response.DeviceDetailResponse;
import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.ButtonOption;
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
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class HomeService {

    private static final int MIN_BUTTON_COUNT = 8;
    private static final int MAX_BUTTON_COUNT = 18;
    private static final Set<String> REQUIRED_DEFAULT_BUTTONS = Set.of(
            "MEDICATION",
            "COMPANION",
            "PHOTO",
            "EMERGENCY"
    );

    private final HomeRepository homeRepository;
    private final ButtonOptionRepository buttonOptionRepository;
    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final UserSeniorRepository userSeniorRepository;
    private final HomeSettingRepository homeSettingRepository;
    private final FamilyRepository familyRepository;
    private static final long DEVICE_OFFLINE_THRESHOLD_MINUTES = 30;

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

        return Home.createButton(
                user,
                buttonRequest.getButtonOrder(),
                buttonName,
                null,
                buttonRequest.getActionType(),
                buttonRequest.getActionValue().trim(),
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
                        "말벗",
                        null,
                        ActionType.DEFAULT,
                        "COMPANION",
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

    private List<Home> getOrCreateInitialHomeButtons(
            User user
    ) {

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        if (!homes.isEmpty()) {
            return homes;
        }

        List<Home> initialButtons =
                createInitialHomeButtons(
                        user
                );

        return homeRepository.saveAll(
                initialButtons
        );
    }

    private List<Home> getDisconnectedPreviewButtons(
            User homeOwner
    ) {
        /*
         * 연결 해제 상태에서는 기존 DB 버튼을 수정하거나 삭제하지 않고
         * 응답에만 초기 기본 런처를 표시
         *
         * TODO 재연결 시 기존 설정 복구 여부가 확정되면 정책 재검토
         */
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

        String trimmedName =
                resolvedName.trim();

        if (trimmedName.length() > 6) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_NAME_TOO_LONG
            );
        }

        return trimmedName;
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

        return lastConnectedAt.isAfter(
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
                .map(this::isExplicitlyDisconnected)
                .orElse(true);
    }

    private boolean isExplicitlyDisconnected(
            Device device
    ) {
        return device.getConnectionStatus()
                == DeviceStatus.DISCONNECTED;
    }


    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            DeviceRepository deviceRepository,
            SeniorRepository seniorRepository,
            UserSeniorRepository userSeniorRepository,
            HomeSettingRepository homeSettingRepository,
            FamilyRepository familyRepository
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.deviceRepository = deviceRepository;
        this.seniorRepository = seniorRepository;
        this.userSeniorRepository = userSeniorRepository;
        this.homeSettingRepository = homeSettingRepository;
        this.familyRepository = familyRepository;
    }

    /**
     * 자녀 홈 화면 조회
     */
    @Transactional
    public HomeResponse getHome() {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        User homeOwner = resolvePrimaryChild(currentUser);

        Optional<User> seniorUser =
                findSeniorUser(currentUser);

        Optional<UserSenior> currentUserSeniorRelation =
                findUserSenior(currentUser);

        Optional<Senior> seniorProfile =
                currentUserSeniorRelation
                        .map(UserSenior::getSenior)
                        .or(() -> findFamilySenior(currentUser));

        boolean deviceDisconnected =
                isDeviceDisconnected(seniorUser);

        HomeResponse.ConnectionResponse connection =
                deviceDisconnected
                        ? HomeResponse.ConnectionResponse.disconnected()
                        : createConnectionResponse(seniorUser);

        List<Home> homes =
                deviceDisconnected
                        ? getDisconnectedPreviewButtons(homeOwner)
                        : getOrCreateInitialHomeButtons(homeOwner);

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
                deviceDisconnected
                        ? HomeResponse.SeniorProfileResponse.empty()
                        : seniorProfile
                        .map(senior ->
                                createSeniorProfileResponse(
                                        senior,
                                        currentUserSeniorRelation
                                                .filter(userSenior ->
                                                        userSenior.getSenior()
                                                                .getSeniorId()
                                                                .equals(
                                                                        senior.getSeniorId()
                                                                )
                                                )
                                                .orElse(null)
                                )
                        )
                        .orElseGet(
                                HomeResponse.SeniorProfileResponse::empty
                        );

        FontSize fontSize =
                deviceDisconnected
                        ? FontSize.MEDIUM
                        : getFontSize(homeOwner);

        HomeResponse.MusicCardResponse musicCard =
                deviceDisconnected
                        ? HomeResponse.MusicCardResponse.empty()
                        : getMusicCardResponse(homeOwner);

        TodayScheduleResponse todaySchedule =
                deviceDisconnected
                        ? null
                        : seniorUser
                        .map(this::getTodayHospitalSchedule)
                        .orElse(null);

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
            SeniorProfileUpdateRequest request
    ) {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        if (currentUser.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
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

        Family lockedFamily =
                familyRepository.findByIdForUpdate(
                                currentUser.getFamily()
                                        .getFamilyId()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.FAMILY_NOT_FOUND
                                )
                        );

        Optional<Senior> familySeniorOptional =
                seniorRepository.findFirstByFamily(lockedFamily);

        Senior senior;

        if (familySeniorOptional.isEmpty()) {

            senior = Senior.builder()
                    .name(request.name())
                    .birth(request.birth())
                    .phoneNumber(normalizedPhoneNumber)
                    .address(request.address())
                    .detailAddress(request.detailAddress())
                    .latitude(request.latitude())
                    .longitude(request.longitude())
                    .family(lockedFamily)
                    .registeredBy(currentUser)
                    .build();

            senior =
                    saveSeniorOrThrowAlreadyExists(
                            senior
                    );

        } else {

            senior =
                    familySeniorOptional.get();

            senior.updateProfile(
                    request.name(),
                    request.birth(),
                    normalizedPhoneNumber,
                    request.address(),
                    request.detailAddress(),
                    request.latitude(),
                    request.longitude()
            );
        }

        UserSenior userSenior =
                upsertUserSenior(
                        currentUser,
                        senior,
                        request.relation(),
                        resolvedCustomRelation
                );

        return SeniorProfileUpdateResponse.from(
                senior,
                userSenior
        );
    }

    /**
     * 홈 버튼과 노래 카드 전체 저장
     */
    @Transactional
    public void saveButtons(
            HomeButtonSaveRequest request
    ) {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

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

        validateSaveButtonRequests(
                buttonRequests
        );

        homeRepository.deleteAllByUser(
                user
        );

        List<Home> newHomes =
                buttonRequests.stream()
                        .map(buttonRequest ->
                                createHomeButton(
                                        user,
                                        buttonRequest
                                )
                        )
                        .toList();

        homeRepository.saveAll(
                newHomes
        );

        HomeSetting homeSetting =
                homeSettingRepository
                        .findByUser(user)
                        .orElseGet(() ->
                                HomeSetting.builder()
                                        .user(user)
                                        .fontSize(FontSize.MEDIUM)
                                        .build()
                        );

        homeSetting.updateMusicApp(
                request.getMusicApp()
        );

        homeSettingRepository.save(
                homeSetting
        );
    }

    /**
     * 기존 홈 버튼 이름, 아이콘, 순서 수정
     */
    @Transactional
    public void updateButtons(
            HomeButtonUpdateRequest request
    ) {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        if (request.getButtons() == null
                || request.getButtons().size() != homes.size()) {

            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        Map<Long, Home> homeMap =
                homes.stream()
                        .collect(
                                Collectors.toMap(
                                        Home::getHomeId,
                                        Function.identity()
                                )
                        );

        boolean hasNullButtonId =
                request.getButtons()
                        .stream()
                        .anyMatch(buttonRequest ->
                                buttonRequest.getButtonId() == null
                        );

        if (hasNullButtonId) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_NOT_FOUND
            );
        }

        long buttonIdCount =
                request.getButtons()
                        .stream()
                        .map(
                                HomeButtonUpdateRequest.ButtonRequest
                                        ::getButtonId
                        )
                        .distinct()
                        .count();

        if (buttonIdCount
                != request.getButtons().size()) {

            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ID
            );
        }

        boolean hasNullButtonOrder =
                request.getButtons()
                        .stream()
                        .anyMatch(buttonRequest ->
                                buttonRequest.getButtonOrder() == null
                        );

        if (hasNullButtonOrder) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_ORDER
            );
        }

        long buttonOrderCount =
                request.getButtons()
                        .stream()
                        .map(
                                HomeButtonUpdateRequest.ButtonRequest
                                        ::getButtonOrder
                        )
                        .distinct()
                        .count();

        if (buttonOrderCount
                != request.getButtons().size()) {

            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ORDER
            );
        }

        List<Integer> sortedOrders =
                request.getButtons()
                        .stream()
                        .map(
                                HomeButtonUpdateRequest.ButtonRequest
                                        ::getButtonOrder
                        )
                        .sorted()
                        .toList();

        validateSequentialOrders(sortedOrders);

        for (HomeButtonUpdateRequest.ButtonRequest buttonRequest
                : request.getButtons()) {

            Home home =
                    homeMap.get(
                            buttonRequest.getButtonId()
                    );

            if (home == null) {
                throw new BusinessException(
                        ErrorCode.HOME_BUTTON_NOT_FOUND
                );
            }

            home.updateButton(
                    buttonRequest.getButtonOrder(),
                    buttonRequest.getButtonName(),
                    buttonRequest.getIcon(),
                    home.getActionType(),
                    home.getActionValue(),
                    home.getPackageName()
            );
        }
    }

    /**
     * 추가 가능한 홈 버튼 옵션 조회
     */
    @Transactional(readOnly = true)
    public List<ButtonOptionResponse> getButtonOptions() {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

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
     * 홈 버튼 개별 추가
     */
    @Transactional
    public HomeButtonCreateResponse createButton(
            HomeButtonCreateRequest request
    ) {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

        ButtonOption buttonOption =
                buttonOptionRepository
                        .findById(request.getOptionId())
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.BUTTON_OPTION_NOT_FOUND
                                )
                        );

        boolean alreadyExists =
                homeRepository
                        .existsByUserAndActionTypeAndActionValue(
                                user,
                                buttonOption.getActionType(),
                                buttonOption.getActionValue()
                        );

        if (alreadyExists) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_ALREADY_EXISTS
            );
        }

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        if (homes.size() >= MAX_BUTTON_COUNT) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_LIMIT_EXCEEDED
            );
        }

        int newButtonOrder =
                homes.stream()
                        .mapToInt(Home::getButtonOrder)
                        .max()
                        .orElse(0)
                        + 1;

        Home newButton =
                Home.createButton(
                        user,
                        newButtonOrder,
                        buttonOption.getButtonName(),
                        buttonOption.getIcon(),
                        buttonOption.getActionType(),
                        buttonOption.getActionValue(),
                        null
                );

        Home savedButton =
                homeRepository.save(
                        newButton
                );

        return HomeButtonCreateResponse.from(
                savedButton
        );
    }

    /**
     * 홈 버튼 개별 삭제
     */
    @Transactional
    public void deleteButton(
            Long buttonId
    ) {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

        Home home =
                homeRepository
                        .findByHomeIdAndUser(
                                buttonId,
                                user
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.HOME_BUTTON_NOT_FOUND
                                )
                        );

        homeRepository.delete(home);

        List<Home> remainingButtons =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        for (int i = 0;
             i < remainingButtons.size();
             i++) {

            remainingButtons
                    .get(i)
                    .updateButtonOrder(i + 1);
        }
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

        User primaryChild =
                findPrimaryChild(parent);

        boolean deviceDisconnected =
                deviceRepository
                        .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                                parent
                        )
                        .map(device ->
                                device.getConnectionStatus()
                                        == DeviceStatus.DISCONNECTED
                        )
                        .orElse(true);

        /*
         * 연결 해제 상태에서는 기존 자녀 설정을 시니어에게 내려주지 않고
         * 초기 기본 런처를 응답한다.
         *
         * TODO 재연결 시 기존 설정 복구 여부가 확정되면 정책 재검토
         */
        List<Home> homes =
                deviceDisconnected
                        ? createInitialHomeButtons(primaryChild)
                        : getOrCreateInitialHomeButtons(primaryChild);

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
                        : getFontSize(primaryChild);

        TodayScheduleResponse todaySchedule =
                deviceDisconnected
                        ? TodayScheduleResponse.empty()
                        : getTodayHospitalSchedule(parent);

        HomeResponse.MusicCardResponse musicCard =
                deviceDisconnected
                        ? HomeResponse.MusicCardResponse.empty()
                        : getMusicCardResponse(primaryChild);

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
    public DeviceDetailResponse getDeviceDetail() {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.CHILD_HOME_ACCESS_DENIED
            );
        }

        Optional<User> seniorUser =
                findSeniorUser(currentUser);

        if (seniorUser.isEmpty()) {
            return DeviceDetailResponse.disconnected();
        }

        return deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        seniorUser.get()
                )
                .map(device -> {

                    if (device.getConnectionStatus()
                            == DeviceStatus.DISCONNECTED) {

                        return DeviceDetailResponse.disconnected();
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
                            connected,
                            device.getLastConnectedAt(),
                            null
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
    public List<TodayHospitalListResponse> getTodayHospitalSchedules() {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_HOME_ACCESS_DENIED
            );
        }

        List<Hospital> hospitals =
                findTodayHospitalSchedules(
                        currentUser
                );

        return hospitals.stream()
                .map(TodayHospitalListResponse::from)
                .toList();
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
            HomeFontSizeUpdateRequest request
    ) {

        User user = getCurrentUser();
        validatePrimaryManager(user);
        validateDeviceConnected(user);

        HomeSetting homeSetting =
                homeSettingRepository
                        .findByUser(user)
                        .orElseGet(() ->
                                HomeSetting.builder()
                                        .user(user)
                                        .fontSize(FontSize.MEDIUM)
                                        .build()
                        );

        homeSetting.updateFontSize(
                request.getFontSize()
        );

        homeSettingRepository.save(
                homeSetting
        );
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

                            if (actionType == null
                                    || actionValue == null
                                    || actionValue.isBlank()) {
                                return true;
                            }

                            if (actionType == ActionType.DEFAULT) {
                                return packageName != null
                                        && !packageName.isBlank();
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

    private User resolvePrimaryChild(
            User currentUser
    ) {

        if (currentUser.getManagerType()
                == ManagerType.PRIMARY) {

            return currentUser;
        }

        return findPrimaryChild(currentUser);
    }

    private User findPrimaryChild(
            User currentUser
    ) {

        if (currentUser.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        List<User> children =
                userRepository
                        .findByFamilyAndUsersIdNotAndRole(
                                currentUser.getFamily(),
                                currentUser.getUsersId(),
                                Role.CHILD
                        );

        return children.stream()
                .filter(child ->
                        child.getManagerType()
                                == ManagerType.PRIMARY
                )
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.PRIMARY_MANAGER_NOT_FOUND
                        )
                );
    }

    private Optional<User> findSeniorUser(
            User currentUser
    ) {

        if (currentUser.getFamily() == null) {
            return Optional.empty();
        }

        List<User> seniors =
                userRepository
                        .findByFamilyAndUsersIdNotAndRole(
                                currentUser.getFamily(),
                                currentUser.getUsersId(),
                                Role.PARENT
                        );

        return seniors.stream()
                .findFirst();
    }

    private Optional<UserSenior> findUserSenior(
            User child
    ) {

        if (child.getFamily() == null) {
            return Optional.empty();
        }

        return userSeniorRepository
                .findFirstByUserAndSenior_Family(
                        child,
                        child.getFamily()
                );
    }

    private Optional<Senior> findFamilySenior(
            User user
    ) {

        if (user.getFamily() == null) {
            return Optional.empty();
        }

        return seniorRepository
                .findFirstByFamily(
                        user.getFamily()
                );
    }

    private HomeResponse.ConnectionResponse createConnectionResponse(
            Optional<User> seniorUser
    ) {

        if (seniorUser.isEmpty()) {
            return HomeResponse
                    .ConnectionResponse
                    .disconnected();
        }

        return deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        seniorUser.get()
                )
                .map(device -> {

                    if (device.getConnectionStatus()
                            == DeviceStatus.DISCONNECTED) {

                        return HomeResponse
                                .ConnectionResponse
                                .disconnected();
                    }

                    return new HomeResponse.ConnectionResponse(
                            device.getDeviceName(),
                            isDeviceConnected(
                                    device.getLastConnectedAt()
                            ),
                            device.getBatteryLevel()
                    );
                })
                .orElseGet(
                        HomeResponse
                                .ConnectionResponse
                                ::disconnected
                );
    }

    private HomeResponse.SeniorProfileResponse
    createSeniorProfileResponse(
            Senior senior,
            UserSenior userSenior
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
                resolveRelation(userSenior),
                birth,
                age,
                senior.getAddress(),
                senior.getDetailAddress(),
                senior.getPhoneNumber()
        );
    }

    private String resolveRelation(
            UserSenior userSenior
    ) {

        if (userSenior == null
                || userSenior.getRelation() == null) {
            return null;
        }

        if (userSenior.getRelation()
                == SeniorRelation.OTHER) {

            return userSenior.getCustomRelation();
        }

        return userSenior.getRelation().name();
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

    private UserSenior upsertUserSenior(
            User user,
            Senior senior,
            SeniorRelation relation,
            String customRelation
    ) {

        validateSameFamily(user, senior);

        UserSenior userSenior =
                userSeniorRepository
                        .findByUserAndSenior(user, senior)
                        .orElseGet(() ->
                                UserSenior.builder()
                                        .user(user)
                                        .senior(senior)
                                        .relation(relation)
                                        .customRelation(customRelation)
                                        .build()
                        );

        userSenior.updateRelation(
                relation,
                customRelation
        );

        return userSeniorRepository.save(userSenior);
    }

    private void validateSameFamily(
            User user,
            Senior senior
    ) {

        if (user.getFamily() == null
                || senior.getFamily() == null
                || !user.getFamily()
                .getFamilyId()
                .equals(
                        senior.getFamily()
                                .getFamilyId()
                )) {

            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }
    }

    private void validateDeviceConnected(
            User child
    ) {
        if (child.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        Optional<User> seniorUser =
                findSeniorUser(child);

        if (isDeviceDisconnected(seniorUser)) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }
    }

    private void validatePrimaryManager(
            User user
    ) {

        if (user.getRole() != Role.CHILD
                || user.getManagerType()
                != ManagerType.PRIMARY) {

            throw new BusinessException(
                    ErrorCode.HOME_SETTING_ACCESS_DENIED
            );
        }
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
