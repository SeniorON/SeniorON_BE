package com.example.senioron.domain.home.service;

import com.example.senioron.domain.home.dto.HomeResponse;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class HomeService {

    private final HomeRepository homeRepository;

    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    public HomeResponse getHome() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        List<Home> homes = homeRepository.findAllByUserOrderByButtonOrderAsc(user);

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
}