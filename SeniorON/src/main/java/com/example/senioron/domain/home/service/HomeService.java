package com.example.senioron.domain.home.service;

import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.home.dto.request.HomeButtonCreateRequest;
import com.example.senioron.domain.home.dto.request.HomeButtonUpdateRequest;
import com.example.senioron.domain.home.dto.request.HomeFontSizeUpdateRequest;
import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.home.dto.response.ButtonOptionResponse;
import com.example.senioron.domain.home.dto.response.HomeButtonCreateResponse;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.dto.response.SeniorHomeResponse;
import com.example.senioron.domain.home.dto.response.SeniorProfileUpdateResponse;
import com.example.senioron.domain.home.dto.response.TodayScheduleResponse;
import com.example.senioron.domain.home.entity.ButtonOption;
import com.example.senioron.domain.home.entity.FontSize;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HomeService {

    private final HomeRepository homeRepository;
    private final ButtonOptionRepository buttonOptionRepository;
    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;

    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            DeviceRepository deviceRepository,
            SeniorRepository seniorRepository
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.deviceRepository = deviceRepository;
        this.seniorRepository = seniorRepository;
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
                                        home.getIcon()
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

        return new HomeResponse(
                currentUser.getName(),
                connection,
                seniorProfileResponse,
                fontSize,
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

        /*
         * 현재 담당자에게 등록된 시니어 정보가 없으면 최초 등록으로 처리
         */
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
     * 홈 버튼 수정
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

        for (int i = 0; i < sortedOrders.size(); i++) {

            if (!sortedOrders.get(i).equals(i + 1)) {
                throw new BusinessException(
                        ErrorCode.INVALID_HOME_BUTTON_ORDER
                );
            }
        }

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
     * 홈 버튼 추가
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
     * 홈 버튼 삭제
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

        /*
         * 병원 일정은 주담당자가 등록하므로
         * 시니어 계정이 아니라 주담당자 계정 기준으로 조회한다.
         */
        TodayScheduleResponse todaySchedule =
                getTodayHospitalSchedule(
                        primaryChild
                );

        return new SeniorHomeResponse(
                fontSize,
                todaySchedule,
                buttons
        );
    }

    /**
     * 오늘 병원 일정 조회
     *
     * 주담당자가 등록한 병원 일정을 조회한다.
     *
     * 일정이 없으면 NONE,
     * 일정이 1개이면 상세 정보,
     * 일정이 2개 이상이면 일정 개수만 반환
     */
    private TodayScheduleResponse getTodayHospitalSchedule(
            User scheduleOwner
    ) {

        LocalDate today =
                LocalDate.now();

        List<Hospital> hospitals =
                hospitalRepository
                        .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                                scheduleOwner,
                                today,
                                today
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
                .findFirstByUser(
                        seniorUser.get()
                )
                .map(device ->
                        new HomeResponse.ConnectionResponse(
                                device.getDeviceName(),
                                device.getConnectionStatus()
                                        == DeviceStatus.ONLINE,
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