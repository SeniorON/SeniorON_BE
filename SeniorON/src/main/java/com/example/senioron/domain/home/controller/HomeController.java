package com.example.senioron.domain.home.controller;

import com.example.senioron.domain.home.dto.request.*;
import com.example.senioron.domain.home.dto.response.*;
import com.example.senioron.domain.home.service.HomeService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    @Operation(
            summary = "부모님 홈 조회",
            description = "부모님 앱에서 글자 크기와 홈 버튼 설정 조회"
    )
    @GetMapping("/senior")
    public Response<SeniorHomeResponse> getSeniorHome() {
        return Response.ok(homeService.getSeniorHome());
    }
    @Operation(
            summary = "홈 글자 크기 수정",
            description = "주 담당자가 부모님 홈 화면의 글자 크기를 수정"
    )
    @PatchMapping("/font-size")
    public Response<Void> updateFontSize(
            @Valid @RequestBody HomeFontSizeUpdateRequest request
    ) {
        homeService.updateFontSize(request);
        return Response.ok();
    }

    @Operation(
            summary = "시니어 프로필 수정",
            description = "주담당자 또는 보조담당자가 공통 시니어 프로필 정보를 수정"
    )
    @PatchMapping("/senior-profile")
    public Response<SeniorProfileUpdateResponse> updateSeniorProfile(
            @Valid @RequestBody SeniorProfileUpdateRequest request
    ) {
        return Response.ok(
                homeService.updateSeniorProfile(request)
        );
    }
    @Operation(
            summary = "홈 버튼 설정 전체 저장",
            description = "노래 카드와 일반 버튼 선택 및 순서를 한 번에 저장"
    )
    @PutMapping("/buttons")
    public Response<Void> saveButtons(
            @Valid @RequestBody HomeButtonSaveRequest request
    ) {
        homeService.saveButtons(request);
        return Response.ok();
    }
    @Operation(
            summary = "오늘 병원 일정 상세 목록 조회",
            description = "가족 내 주담당자와 보조담당자가 등록한 오늘 병원 일정을 시간순으로 조회"
    )
    @GetMapping("/hospitals/today")
    public Response<List<TodayHospitalListResponse>>
    getTodayHospitalSchedules() {

        return Response.ok(
                homeService.getTodayHospitalSchedules()
        );
    }
}