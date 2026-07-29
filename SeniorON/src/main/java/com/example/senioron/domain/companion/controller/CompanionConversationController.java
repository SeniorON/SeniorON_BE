package com.example.senioron.domain.companion.controller;

import com.example.senioron.domain.companion.dto.response.CompanionConversationEndResponse;
import com.example.senioron.domain.companion.dto.response.CompanionConversationStartResponse;
import com.example.senioron.domain.companion.service.CompanionConversationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "말벗 대화", description = "말벗 대화 세션 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companion/conversations")
public class CompanionConversationController {

    private final CompanionConversationService conversationService;

    @Operation(summary = "말벗 대화 시작", description = "부모 사용자의 말벗 대화를 시작합니다. " + "활성 대화가 이미 존재하면 기존 대화를 반환합니다.")
    @PostMapping
    public Response<CompanionConversationStartResponse> start(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(conversationService.start(user));
    }

    @Operation(summary = "말벗 대화 종료", description = "로그인한 부모 사용자 소유의 대화를 종료합니다. " + "이미 종료된 대화는 기존 종료 정보를 반환합니다.")
    @PostMapping("/{conversationId}/end")
    public Response<CompanionConversationEndResponse> end(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId
    ) {
        return Response.ok(conversationService.end(user, conversationId));
    }

}
