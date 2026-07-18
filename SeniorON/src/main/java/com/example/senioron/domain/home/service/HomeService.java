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
import com.example.senioron.domain.home.dto.response.ScheduleType;
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
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.repository.MedicationRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private final MedicationRepository medicationRepository;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;

    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            MedicationRepository medicationRepository,
            DeviceRepository deviceRepository,
            SeniorRepository seniorRepository
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.medicationRepository = medicationRepository;
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

        /*
         * OTHER 관계인 경우 직접 입력한 관계가 반드시 존재해야 합니다.
         */
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
         * 현재 담당자에게 등록된 시니어 정보가 없으면
         * 최초 등록으로 처리합니다.
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

            /*
             * 기존 정보가 있으면 공통 프로필 정보만 수정합니다.
             *
             * relation과 customRelation은 담당자별로 다를 수 있으므로
             * 기존 값을 유지합니다.
             */
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

        /*
         * 같은 가족의 다른 자녀 담당자가 이미 등록한 Senior 정보가 있다면
         * 이름, 생년월일, 전화번호, 주소 등 공통 정보만 동기화합니다.
         *
         * 다른 담당자의 Senior 정보가 없는 경우에는 관계를 알 수 없으므로
         * 서버에서 임의로 새 행을 생성하지 않습니다.
         */
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

        List<TodayScheduleResponse> todaySchedules =
                getTodaySchedules(parent);

        return new SeniorHomeResponse(
                fontSize,
                todaySchedules,
                buttons
        );
    }

    private List<TodayScheduleResponse> getTodaySchedules(
            User parent
    ) {

        LocalDate today = LocalDate.now();

        List<TodayScheduleResponse> schedules =
                new ArrayList<>();

        List<Hospital> hospitals =
                hospitalRepository
                        .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                                parent,
                                today,
                                today
                        );

        for (Hospital hospital : hospitals) {

            schedules.add(
                    TodayScheduleResponse.builder()
                            .scheduleId(
                                    hospital.getHospital_id()
                            )
                            .scheduleType(
                                    ScheduleType.HOSPITAL
                            )
                            .title(
                                    hospital.getHospitalName()
                            )
                            .description(
                                    hospital.getDepartment()
                            )
                            .scheduledTime(
                                    hospital.getScheduleTime()
                            )
                            .build()
            );
        }

        List<Medication> medications =
                medicationRepository
                        .findAllByUserOrderByMedicineTimeAsc(
                                parent
                        );

        for (Medication medication : medications) {

            if (!isMedicationScheduledToday(
                    medication,
                    today
            )) {
                continue;
            }

            schedules.add(
                    TodayScheduleResponse.builder()
                            .scheduleId(
                                    medication.getMedication_id()
                            )
                            .scheduleType(
                                    ScheduleType.MEDICATION
                            )
                            .title(
                                    medication.getMedicineName()
                            )
                            .description(
                                    medication.getIngredientName()
                            )
                            .scheduledTime(
                                    medication.getMedicineTime()
                            )
                            .build()
            );
        }

        schedules.sort(
                Comparator.comparing(
                        TodayScheduleResponse::getScheduledTime,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                )
        );

        return schedules;
    }

    private boolean isMedicationScheduledToday(
            Medication medication,
            LocalDate today
    ) {

        String medicineDays =
                medication.getMedicineDays();

        if (medicineDays == null
                || medicineDays.isBlank()) {

            return false;
        }

        DayOfWeek dayOfWeek =
                today.getDayOfWeek();

        String koreanDay =
                convertToKoreanDay(dayOfWeek);

        String shortEnglishDay =
                dayOfWeek
                        .name()
                        .substring(0, 3);

        String fullEnglishDay =
                dayOfWeek.name();

        return Arrays.stream(
                        medicineDays.split(",")
                )
                .map(String::trim)
                .filter(day ->
                        !day.isBlank()
                )
                .map(String::toUpperCase)
                .anyMatch(day ->
                        day.equals(koreanDay)
                                || day.equals(
                                shortEnglishDay
                        )
                                || day.equals(
                                fullEnglishDay
                        )
                );
    }

    private String convertToKoreanDay(
            DayOfWeek dayOfWeek
    ) {

        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

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

    /**
     * OTHER 관계라면 직접 입력한 관계값을 검증합니다.
     *
     * 별도의 ErrorCode를 추가하지 않고 HomeService 내부에서 검증해
     * Senior 도메인의 변경을 피합니다.
     */
    private String resolveCustomRelation(
            SeniorRelation relation,
            String customRelation
    ) {

        if (relation != SeniorRelation.OTHER) {
            return null;
        }

        if (customRelation == null
                || customRelation.isBlank()) {

            throw new IllegalArgumentException(
                    "기타 관계를 직접 입력해 주세요."
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