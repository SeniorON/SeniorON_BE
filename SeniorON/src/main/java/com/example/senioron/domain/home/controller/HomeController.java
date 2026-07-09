package com.example.senioron.domain.home.controller;

import com.example.senioron.domain.home.dto.HomeResponse;
import com.example.senioron.domain.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.senioron.global.apiPayload.response.Response;

@Tag(name = "홈", description = "홈 화면 관련 API")
@RestController
@RequestMapping("/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "홈 메인 조회", description = "로그인한 사용자의 홈 메인 화면 정보 조회")
    @GetMapping
    public Response<HomeResponse> getHome() {
        return Response.ok(homeService.getHome());
    }
}