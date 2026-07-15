package com.example.senioron.domain.home.service;

import com.example.senioron.domain.home.dto.request.HomeButtonCreateRequest;
import com.example.senioron.domain.home.dto.request.HomeButtonUpdateRequest;
import com.example.senioron.domain.home.dto.response.ButtonOptionResponse;
import com.example.senioron.domain.home.dto.response.HomeButtonCreateResponse;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.dto.response.SeniorHomeResponse;
import com.example.senioron.domain.home.entity.ButtonOption;
import com.example.senioron.domain.home.entity.FontSize;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HomeService {

    private final HomeRepository homeRepository;
    private final ButtonOptionRepository buttonOptionRepository;
    private final UserRepository userRepository;

    public HomeService(
            HomeRepository homeRepository,
            ButtonOptionRepository buttonOptionRepository,
            UserRepository userRepository
    ) {
        this.homeRepository = homeRepository;
        this.buttonOptionRepository = buttonOptionRepository;
        this.userRepository = userRepository;
    }

    public HomeResponse getHome() {

        User user = getCurrentUser();

        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(user);

        LocalDate birth = user.getBirth();

        List<HomeResponse.HomeButtonResponse> buttons = homes.stream()
                .map(home -> new HomeResponse.HomeButtonResponse(
                        home.getHomeId(),
                        home.getButtonOrder(),
                        home.getButtonName(),
                        home.getIcon()
                ))
                .toList();

        Integer age = birth == null
                ? null
                : Period.between(birth, LocalDate.now()).getYears();

        return new HomeResponse(
                user.getName(),
                null,
                new HomeResponse.SeniorProfileResponse(
                        user.getName(),
                        null,
                        birth,
                        age,
                        null,
                        user.getPhoneNumber()
                ),
                homes.isEmpty()
                        ? null
                        : homes.get(0).getFontSize(),
                buttons
        );
    }

    @Transactional
    public void updateButtons(HomeButtonUpdateRequest request) {

        User user = getCurrentUser();

        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(user);

        if (request.getButtons() == null
                || request.getButtons().size() != homes.size()) {

            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_REQUEST
            );
        }

        Map<Long, Home> homeMap = homes.stream()
                .collect(Collectors.toMap(
                        Home::getHomeId,
                        Function.identity()
                ));

        boolean hasNullButtonId = request.getButtons().stream()
                .anyMatch(buttonRequest ->
                        buttonRequest.getButtonId() == null
                );

        if (hasNullButtonId) {
            throw new BusinessException(
                    ErrorCode.HOME_BUTTON_NOT_FOUND
            );
        }

        long buttonIdCount = request.getButtons().stream()
                .map(HomeButtonUpdateRequest.ButtonRequest::getButtonId)
                .distinct()
                .count();

        if (buttonIdCount != request.getButtons().size()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ID
            );
        }

        boolean hasNullButtonOrder = request.getButtons().stream()
                .anyMatch(buttonRequest ->
                        buttonRequest.getButtonOrder() == null
                );

        if (hasNullButtonOrder) {
            throw new BusinessException(
                    ErrorCode.INVALID_HOME_BUTTON_ORDER
            );
        }

        long buttonOrderCount = request.getButtons().stream()
                .map(HomeButtonUpdateRequest.ButtonRequest::getButtonOrder)
                .distinct()
                .count();

        if (buttonOrderCount != request.getButtons().size()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_HOME_BUTTON_ORDER
            );
        }

        List<Integer> sortedOrders = request.getButtons().stream()
                .map(HomeButtonUpdateRequest.ButtonRequest::getButtonOrder)
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

            Home home = homeMap.get(buttonRequest.getButtonId());

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

    public List<ButtonOptionResponse> getButtonOptions() {

        return buttonOptionRepository.findAll()
                .stream()
                .map(option -> new ButtonOptionResponse(
                        option.getOptionId(),
                        option.getButtonName(),
                        option.getIcon(),
                        option.getActionType(),
                        option.getActionValue()
                ))
                .toList();
    }

    @Transactional
    public HomeButtonCreateResponse createButton(
            HomeButtonCreateRequest request
    ) {

        User user = getCurrentUser();

        ButtonOption buttonOption = buttonOptionRepository
                .findById(request.getOptionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUTTON_OPTION_NOT_FOUND
                ));

        boolean alreadyExists =
                homeRepository.existsByUserAndActionTypeAndActionValue(
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
                homeRepository.findAllByUserOrderByButtonOrderAsc(user);

        int newButtonOrder = homes.stream()
                .mapToInt(Home::getButtonOrder)
                .max()
                .orElse(0)
                + 1;

        FontSize fontSize = homes.isEmpty()
                ? FontSize.MEDIUM
                : homes.get(0).getFontSize();

        Home newButton = Home.createButton(
                user,
                newButtonOrder,
                buttonOption.getButtonName(),
                buttonOption.getIcon(),
                buttonOption.getActionType(),
                buttonOption.getActionValue(),
                fontSize
        );

        Home savedButton = homeRepository.save(newButton);

        return HomeButtonCreateResponse.from(savedButton);
    }

    @Transactional
    public void deleteButton(Long buttonId) {

        User user = getCurrentUser();

        Home home = homeRepository
                .findByHomeIdAndUser(buttonId, user)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.HOME_BUTTON_NOT_FOUND
                ));

        homeRepository.delete(home);

        List<Home> remainingButtons =
                homeRepository.findAllByUserOrderByButtonOrderAsc(user);

        for (int i = 0; i < remainingButtons.size(); i++) {
            remainingButtons.get(i).updateButtonOrder(i + 1);
        }
    }

    public SeniorHomeResponse getSeniorHome() {

        User parent = getCurrentUser();

        /*
         * 현재 로그인한 부모님과 같은 Family에 속한
         * CHILD 역할의 사용자들을 조회한다.
         *
         * UserRepository는 수정하지 않고
         * 기존 메서드를 그대로 사용한다.
         */
        List<User> children =
                userRepository.findByFamilyAndUsersIdNotAndRole(
                        parent.getFamily(),
                        parent.getUsersId(),
                        Role.CHILD
                );

        /*
         * 자녀들 중 주 담당자(PRIMARY)를 찾는다.
         */
        User primaryChild = children.stream()
                .filter(child ->
                        child.getManagerType() == ManagerType.PRIMARY
                )
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.HOME_BUTTON_NOT_FOUND
                ));

        /*
         * 주 담당자가 설정한 홈 버튼들을 조회한다.
         */
        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(
                        primaryChild
                );

        List<SeniorHomeResponse.ButtonResponse> buttons = homes.stream()
                .map(home -> new SeniorHomeResponse.ButtonResponse(
                        home.getHomeId(),
                        home.getButtonOrder(),
                        home.getButtonName(),
                        home.getIcon(),
                        home.getActionType(),
                        home.getActionValue()
                ))
                .toList();

        FontSize fontSize = homes.isEmpty()
                ? FontSize.MEDIUM
                : homes.get(0).getFontSize();

        return new SeniorHomeResponse(
                fontSize,
                buttons
        );
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return (User) authentication.getPrincipal();
    }
}