package com.example.senioron.domain.event.controller;

import com.example.senioron.domain.event.dto.request.InactivityRequest;
import com.example.senioron.domain.event.dto.request.OutingReturnRequest;
import com.example.senioron.domain.event.dto.request.RiskLinkRequest;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.EventDetailResponse;
import com.example.senioron.domain.event.dto.response.InactivityResponse;
import com.example.senioron.domain.event.dto.response.OutingReturnResponse;
import com.example.senioron.domain.event.dto.response.RiskLinkResponse;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.service.EventService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "이벤트",description = "이벤트 관련 메소드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/event")
public class EventController {
    private final EventService eventService;

    @Operation(summary = "SOS 긴급 이벤트 생성", description = "사용자의 SOS 긴급 호출 이벤트를 생성합니다")
    @PostMapping("/sos")
    public Response<SosEventResponse> createSosEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SosEventRequest req
    ){
        return Response.ok(eventService.createSosEvent(user, req));

    }
    @Operation(summary = "미활동 이벤트 생성", description = "사용자의 미활동 이벤트를 생성합니다")
    @PostMapping("/inactivity")
    public Response<InactivityResponse> createInactivityEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody InactivityRequest req
    ) {
        return Response.ok(eventService.createInactivityEvent(user, req));
    }
    @Operation(summary = "외출,귀가 이벤트 생성", description = "사용자의 외출,귀가 이벤트를 생성합니다")
    @PostMapping("/outing-return")
    public Response<OutingReturnResponse> createOutingReturnEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody OutingReturnRequest request
    ) {
        return Response.ok(eventService.createOutingReturnEvent(user, request));
    }

    @Operation(summary = "위험링크 감지 이벤트 생성", description = "사용자 기기에서 감지된 위험링크 이벤트를 생성합니다")
    @PostMapping("/risk-link")
    public Response<RiskLinkResponse> createRiskLinkEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RiskLinkRequest req
    ) {
        return Response.ok(eventService.createRiskLinkEvent(user, req));
    }

    @Operation(summary = "이벤트 상세조회", description = "각 이벤트의 상세내역을 조회합니다.")
    @GetMapping("/{eventId}")
    public Response<EventDetailResponse> getEventDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Long eventId
            ) {
        return Response.ok(eventService.getEventDetail(user, eventId));
    }
}
