package com.example.senioron.domain.home.service;

import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
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
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class HomeService {

    private static final int MAX_BUTTON_COUNT_WITH_MUSIC_CARD = 10;
    private static final int MAX_BUTTON_COUNT_WITHOUT_MUSIC_CARD = 12;

    private final HomeRepository homeRepository;
    private final ButtonOptionRepository buttonOptionRepository;
    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final HomeSettingRepository homeSettingRepository;
    private static final long DEVICE_OFFLINE_THRESHOLD_MINUTES = 30;

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


    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            DeviceRepository deviceRepository,
            SeniorRepository seniorRepository,
            HomeSettingRepository homeSettingRepository
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.deviceRepository = deviceRepository;
        this.seniorRepository = seniorRepository;
        this.homeSettingRepository = homeSettingRepository;
    }

    /**
     * 자녀 홈 화면 조회
     */
    @Transactional(readOnly = true)
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

        Optional<Senior> seniorProfile =
                findRegisteredSenior(currentUser);

        HomeResponse.ConnectionResponse connection =
                createConnectionResponse(seniorUser);

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                homeOwner
                        );

        List<HomeResponse.HomeButtonResponse> buttons =
                homes.stream()
                        .map(home ->
                                new HomeResponse.HomeButtonResponse(
                                        home.getHomeId(),
                                        home.getButtonOrder(),
                                        home.getButtonName(),
                                        home.getIcon(),
                                        home.getActionType(),
                                        home.getActionValue()
                                )
                        )
                        .toList();

        HomeResponse.SeniorProfileResponse seniorProfileResponse =
                seniorProfile
                        .map(this::createSeniorProfileResponse)
                        .orElseGet(
                                HomeResponse.SeniorProfileResponse::empty
                        );

        FontSize fontSize =
                homes.isEmpty()
                        ? FontSize.MEDIUM
                        : homes.get(0).getFontSize();

        HomeResponse.MusicCardResponse musicCard =
                getMusicCardResponse(homeOwner);

        TodayScheduleResponse todaySchedule =
                seniorUser
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

        Optional<Senior> currentUserSeniorOptional =
                findRegisteredSenior(currentUser);

        Senior currentUserSenior;

        if (currentUserSeniorOptional.isEmpty()) {

            currentUserSenior = Senior.builder()
                    .name(request.name())
                    .relation(request.relation())
                    .customRelation(resolvedCustomRelation)
                    .birth(request.birth())
                    .phoneNumber(normalizedPhoneNumber)
                    .address(request.address())
                    .detailAddress(request.detailAddress())
                    .registeredBy(currentUser)
                    .build();

            currentUserSenior =
                    seniorRepository.save(
                            currentUserSenior
                    );

        } else {

            currentUserSenior =
                    currentUserSeniorOptional.get();

            currentUserSenior.updateProfile(
                    request.name(),
                    request.birth(),
                    normalizedPhoneNumber,
                    request.address(),
                    request.detailAddress()
            );
        }

        List<User> familyMembers =
                userRepository.findAllByFamily(
                        currentUser.getFamily()
                );

        List<User> otherChildManagers =
                familyMembers.stream()
                        .filter(user ->
                                user.getRole() == Role.CHILD
                        )
                        .filter(user ->
                                !user.getUsersId()
                                        .equals(
                                                currentUser.getUsersId()
                                        )
                        )
                        .toList();

        for (User childManager : otherChildManagers) {

            seniorRepository
                    .findFirstByRegisteredBy(
                            childManager
                    )
                    .ifPresent(senior ->
                            senior.updateProfile(
                                    request.name(),
                                    request.birth(),
                                    normalizedPhoneNumber,
                                    request.address(),
                                    request.detailAddress()
                            )
                    );
        }

        return SeniorProfileUpdateResponse.from(
                currentUserSenior
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

        List<HomeButtonSaveRequest.ButtonRequest> buttonRequests =
                request.getButtons();

        if (buttonRequests == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        int maxButtonCount =
                request.getMusicApp() == null
                        ? MAX_BUTTON_COUNT_WITHOUT_MUSIC_CARD
                        : MAX_BUTTON_COUNT_WITH_MUSIC_CARD;

        if (buttonRequests.size() > maxButtonCount) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_LIMIT_EXCEEDED
            );
        }

        validateSaveButtonRequests(buttonRequests);

        Set<Long> optionIds =
                buttonRequests.stream()
                        .map(
                                HomeButtonSaveRequest.ButtonRequest
                                        ::getOptionId
                        )
                        .collect(Collectors.toSet());

        Map<Long, ButtonOption> optionMap =
                buttonOptionRepository
                        .findAllById(optionIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ButtonOption::getOptionId,
                                        Function.identity()
                                )
                        );

        if (optionMap.size() != optionIds.size()) {
            throw new BusinessException(
                    ErrorCode.BUTTON_OPTION_NOT_FOUND
            );
        }

        List<Home> existingHomes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        FontSize fontSize =
                existingHomes.isEmpty()
                        ? FontSize.MEDIUM
                        : existingHomes.get(0).getFontSize();

        homeRepository.deleteAllByUser(user);

        List<Home> newHomes =
                buttonRequests.stream()
                        .map(buttonRequest -> {

                            ButtonOption option =
                                    optionMap.get(
                                            buttonRequest.getOptionId()
                                    );

                            return Home.createButton(
                                    user,
                                    buttonRequest.getButtonOrder(),
                                    option.getButtonName(),
                                    option.getIcon(),
                                    option.getActionType(),
                                    option.getActionValue(),
                                    fontSize
                            );
                        })
                        .toList();

        homeRepository.saveAll(newHomes);

        HomeSetting homeSetting =
                homeSettingRepository
                        .findByUser(user)
                        .orElseGet(() ->
                                HomeSetting.builder()
                                        .user(user)
                                        .build()
                        );

        homeSetting.updateMusicApp(
                request.getMusicApp()
        );

        homeSettingRepository.save(homeSetting);
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
                    buttonRequest.getIcon()
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

        MusicApp musicApp =
                homeSettingRepository
                        .findByUser(user)
                        .map(HomeSetting::getMusicApp)
                        .orElse(null);

        int maxButtonCount =
                musicApp == null
                        ? MAX_BUTTON_COUNT_WITHOUT_MUSIC_CARD
                        : MAX_BUTTON_COUNT_WITH_MUSIC_CARD;

        if (homes.size() >= maxButtonCount) {
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

        FontSize fontSize =
                homes.isEmpty()
                        ? FontSize.MEDIUM
                        : homes.get(0).getFontSize();

        Home newButton =
                Home.createButton(
                        user,
                        newButtonOrder,
                        buttonOption.getButtonName(),
                        buttonOption.getIcon(),
                        buttonOption.getActionType(),
                        buttonOption.getActionValue(),
                        fontSize
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
    @Transactional(readOnly = true)
    public SeniorHomeResponse getSeniorHome() {

        User parent = getCurrentUser();

        if (parent.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_HOME_ACCESS_DENIED
            );
        }

        User primaryChild =
                findPrimaryChild(parent);

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                primaryChild
                        );

        List<SeniorHomeResponse.ButtonResponse> buttons =
                homes.stream()
                        .map(home ->
                                new SeniorHomeResponse.ButtonResponse(
                                        home.getHomeId(),
                                        home.getButtonOrder(),
                                        home.getButtonName(),
                                        home.getIcon(),
                                        home.getActionType(),
                                        home.getActionValue()
                                )
                        )
                        .toList();

        FontSize fontSize =
                homes.isEmpty()
                        ? FontSize.MEDIUM
                        : homes.get(0).getFontSize();

        TodayScheduleResponse todaySchedule =
                getTodayHospitalSchedule(
                        parent
                );

        HomeResponse.MusicCardResponse musicCard =
                getMusicCardResponse(primaryChild);

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
            User currentUser
    ) {

        if (currentUser.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        LocalDate today =
                LocalDate.now();

        List<User> childManagers =
                userRepository
                        .findAllByFamily(
                                currentUser.getFamily()
                        )
                        .stream()
                        .filter(user ->
                                user.getRole() == Role.CHILD
                        )
                        .filter(user ->
                                user.getManagerType()
                                        == ManagerType.PRIMARY
                                        || user.getManagerType()
                                        == ManagerType.SUB
                        )
                        .toList();

        return childManagers.stream()
                .flatMap(childManager ->
                        hospitalRepository
                                .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                                        childManager,
                                        today,
                                        today
                                )
                                .stream()
                )
                .sorted(
                        Comparator.comparing(
                                        Hospital::getScheduleDate
                                )
                                .thenComparing(
                                        Hospital::getScheduleTime
                                )
                )
                .toList();
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

        List<Home> homes =
                homeRepository
                        .findAllByUserOrderByButtonOrderAsc(
                                user
                        );

        if (homes.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_NOT_FOUND
            );
        }

        for (Home home : homes) {
            home.updateFontSize(
                    request.getFontSize()
            );
        }
    }

    private void validateSaveButtonRequests(
            List<HomeButtonSaveRequest.ButtonRequest> buttonRequests
    ) {

        boolean hasNullOptionId =
                buttonRequests.stream()
                        .anyMatch(buttonRequest ->
                                buttonRequest.getOptionId() == null
                        );

        if (hasNullOptionId) {
            throw new BusinessException(
                    ErrorCode.BUTTON_OPTION_NOT_FOUND
            );
        }

        long optionIdCount =
                buttonRequests.stream()
                        .map(
                                HomeButtonSaveRequest.ButtonRequest
                                        ::getOptionId
                        )
                        .distinct()
                        .count();

        if (optionIdCount != buttonRequests.size()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ID
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

        return switch (musicApp) {

            case MELON ->
                    new HomeResponse.MusicCardResponse(
                            true,
                            MusicApp.MELON,
                            "멜론",
                            "melon",
                            ActionType.APP,
                            "melon"
                    );

            case SPOTIFY ->
                    new HomeResponse.MusicCardResponse(
                            true,
                            MusicApp.SPOTIFY,
                            "스포티파이",
                            "spotify",
                            ActionType.APP,
                            "spotify"
                    );
        };
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

    private Optional<Senior> findRegisteredSenior(
            User child
    ) {

        return seniorRepository
                .findFirstByRegisteredBy(
                        child
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
                .map(device ->
                        new HomeResponse.ConnectionResponse(
                                device.getDeviceName(),
                                isDeviceConnected(
                                        device.getLastConnectedAt()
                                ),
                                device.getBatteryLevel()
                        )
                )
                .orElseGet(
                        HomeResponse
                                .ConnectionResponse
                                ::disconnected
                );
    }

    private HomeResponse.SeniorProfileResponse
    createSeniorProfileResponse(
            Senior senior
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
                senior.getName(),
                resolveRelation(senior),
                birth,
                age,
                createFullAddress(senior),
                senior.getPhoneNumber()
        );
    }

    private String resolveRelation(
            Senior senior
    ) {

        if (senior.getRelation() == null) {
            return null;
        }

        if (senior.getRelation()
                == SeniorRelation.OTHER) {

            return senior.getCustomRelation();
        }

        return senior.getRelation().name();
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

    private String createFullAddress(
            Senior senior
    ) {

        String address =
                senior.getAddress();

        String detailAddress =
                senior.getDetailAddress();

        if (address == null
                || address.isBlank()) {

            return detailAddress;
        }

        if (detailAddress == null
                || detailAddress.isBlank()) {

            return address;
        }

        return address
                + " "
                + detailAddress;
    }

    private String normalizePhoneNumber(
            String phoneNumber
    ) {

        return phoneNumber
                .replace("-", "")
                .replace(" ", "");
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