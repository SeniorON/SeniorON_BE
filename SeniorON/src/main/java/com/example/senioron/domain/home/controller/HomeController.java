package com.example.senioron.domain.home.controller;

import com.example.senioron.domain.home.dto.request.*;
import com.example.senioron.domain.home.dto.response.*;
import com.example.senioron.domain.device.dto.response.DeviceDetailResponse;
import com.example.senioron.domain.home.service.HomeService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "홈", description = "홈 화면 관련 API")
@RestController
@RequestMapping("/api/home")
@Slf4j
public class HomeController {

    private final HomeService homeService;

    public HomeController(
            HomeService homeService
    ) {
        this.homeService = homeService;
    }

    @Operation(
            summary = "홈 메인 조회",
            description = "로그인한 사용자의 홈 메인 화면 정보 조회"
    )
    @GetMapping
    public Response<HomeResponse> getHome() {
        long start = System.nanoTime();
        Response<HomeResponse> response = Response.ok(homeService.getHome());
        log.debug("[TIMING] HomeController.getHome : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }

    @Operation(
            summary = "추가 가능한 홈 버튼 목록 조회",
            description = "사용자가 홈 화면에 추가할 수 있는 버튼 옵션 목록 조회"
    )
    @GetMapping("/button-options")
    public Response<List<ButtonOptionResponse>> getButtonOptions() {
        long start = System.nanoTime();
        Response<List<ButtonOptionResponse>> response = Response.ok(homeService.getButtonOptions());
        log.debug("[TIMING] HomeController.getButtonOptions : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }
    @Operation(
            summary = "부모님 홈 조회",
            description = "부모님 앱에서 글자 크기와 홈 버튼 설정 조회"
    )
    @GetMapping("/senior")
    public Response<SeniorHomeResponse> getSeniorHome() {
        long start = System.nanoTime();
        Response<SeniorHomeResponse> response = Response.ok(homeService.getSeniorHome());
        log.debug("[TIMING] HomeController.getSeniorHome : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }
    @Operation(
            summary = "홈 글자 크기 수정",
            description = "주 담당자가 부모님 홈 화면의 글자 크기를 수정"
    )
    @PatchMapping("/font-size")
    public Response<Void> updateFontSize(
            @Valid @RequestBody HomeFontSizeUpdateRequest request
    ) {
        long start = System.nanoTime();
        homeService.updateFontSize(request);
        log.debug("[TIMING] HomeController.updateFontSize : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
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
        long start = System.nanoTime();
        Response<SeniorProfileUpdateResponse> response = Response.ok(homeService.updateSeniorProfile(request));
        log.debug("[TIMING] HomeController.updateSeniorProfile : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }
    @Operation(
            summary = "홈 버튼 설정 전체 저장",
            description = "노래 카드와 일반 버튼 선택 및 순서를 한 번에 저장"
    )
    @PutMapping("/buttons")
    public Response<Void> saveButtons(
            @Valid @RequestBody HomeButtonSaveRequest request
    ) {
        long start = System.nanoTime();
        homeService.saveButtons(request);
        log.debug("[TIMING] HomeController.saveButtons : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return Response.ok();
    }
    @Operation(
            summary = "오늘 병원 일정 상세 목록 조회",
            description = "시니어가 가족 내 주담당자와 보조담당자가 등록한 오늘 병원 일정을 시간순으로 조회"
    )
    @GetMapping("/hospitals/today")
    public Response<List<TodayHospitalListResponse>>
    getTodayHospitalSchedules() {

        long start = System.nanoTime();
        Response<List<TodayHospitalListResponse>> response = Response.ok(homeService.getTodayHospitalSchedules());
        log.debug("[TIMING] HomeController.getTodayHospitalSchedules : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }
    @Operation(
            summary = "시니어 기기 연결 상태 상세 조회",
            description = "자녀가 연결된 시니어 기기의 기기명, 연결 상태, 배터리 및 마지막 연결 시각을 조회"
    )
    @GetMapping("/device")
    public Response<DeviceDetailResponse> getDeviceDetail() {

        long start = System.nanoTime();
        Response<DeviceDetailResponse> response = Response.ok(homeService.getDeviceDetail());
        log.debug("[TIMING] HomeController.getDeviceDetail : {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
    }
}