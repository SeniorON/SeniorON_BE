package com.example.senioron.domain.home.service;

import com.example.senioron.domain.home.dto.request.HomeButtonUpdateRequest;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.user.entity.User;
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

    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
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

        return new HomeResponse(
                user.getName(),
                null,
                new HomeResponse.SeniorProfileResponse(
                        user.getName(),
                        null,
                        birth,
                        Period.between(birth, LocalDate.now()).getYears(),
                        null,
                        user.getPhoneNumber()
                ),
                homes.isEmpty() ? null : homes.get(0).getFontSize(),
                buttons
        );
    }

    @Transactional
    public void updateButtons(HomeButtonUpdateRequest request) {

        User user = getCurrentUser();

        List<Home> homes =
                homeRepository.findAllByUserOrderByButtonOrderAsc(user);

        Map<Long, Home> homeMap = homes.stream()
                .collect(Collectors.toMap(
                        Home::getHomeId,
                        Function.identity()
                ));

        for (HomeButtonUpdateRequest.ButtonRequest buttonRequest
                : request.getButtons()) {

            Home home = homeMap.get(buttonRequest.getButtonId());

            if (home == null) {
                throw new BusinessException(ErrorCode.HOME_BUTTON_NOT_FOUND);
            }

            home.updateButton(
                    buttonRequest.getButtonOrder(),
                    buttonRequest.getButtonName(),
                    buttonRequest.getIcon()
            );
        }
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (User) authentication.getPrincipal();
    }
}