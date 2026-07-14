package com.example.senioron.domain.home.controller;

import com.example.senioron.domain.home.dto.request.HomeButtonCreateRequest;
import com.example.senioron.domain.home.dto.request.HomeButtonUpdateRequest;
import com.example.senioron.domain.home.dto.response.ButtonOptionResponse;
import com.example.senioron.domain.home.dto.response.HomeButtonCreateResponse;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.service.HomeService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "홈", description = "홈 화면 관련 API")
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(
            summary = "홈 메인 조회",
            description = "로그인한 사용자의 홈 메인 화면 정보 조회"
    )
    @GetMapping
    public Response<HomeResponse> getHome() {
        return Response.ok(homeService.getHome());
    }

    @Operation(
            summary = "홈 버튼 수정",
            description = "로그인한 사용자의 홈 버튼 순서, 이름, 아이콘 수정"
    )
    @PatchMapping("/buttons")
    public Response<Void> updateButtons(
            @Valid @RequestBody HomeButtonUpdateRequest request
    ) {
        homeService.updateButtons(request);
        return Response.ok();
    }

    @Operation(
            summary = "홈 버튼 추가",
            description = "선택한 버튼 옵션을 로그인한 사용자의 홈 화면에 추가"
    )
    @PostMapping("/buttons")
    public Response<HomeButtonCreateResponse> createButton(
            @Valid @RequestBody HomeButtonCreateRequest request
    ) {
        return Response.ok(homeService.createButton(request));
    }

    @Operation(
            summary = "추가 가능한 홈 버튼 목록 조회",
            description = "사용자가 홈 화면에 추가할 수 있는 버튼 옵션 목록 조회"
    )
    @GetMapping("/button-options")
    public Response<List<ButtonOptionResponse>> getButtonOptions() {
        return Response.ok(homeService.getButtonOptions());
    }

    @Operation(
            summary = "홈 버튼 삭제",
            description = "로그인한 사용자의 홈 버튼을 삭제하고 남은 버튼 순서를 재정렬"
    )
    @DeleteMapping("/buttons/{buttonId}")
    public Response<Void> deleteButton(
            @PathVariable Long buttonId
    ) {
        homeService.deleteButton(buttonId);
        return Response.ok();
    }
}