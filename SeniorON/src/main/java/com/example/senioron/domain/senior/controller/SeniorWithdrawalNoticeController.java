package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.response.SeniorWithdrawalNoticeResponse;
import com.example.senioron.domain.senior.service.SeniorWithdrawalNoticeService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시니어 탈퇴 알림", description = "시니어 탈퇴 사실과 보호자별 확인 상태 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seniors/withdrawal-notices")
public class SeniorWithdrawalNoticeController {

    private final SeniorWithdrawalNoticeService noticeService;

    @Operation(
            summary = "미확인 시니어 탈퇴 알림 조회",
            description = "로그인한 자녀에게 발급된 미확인·미만료 시니어 탈퇴 알림을 최신 탈퇴순으로 조회합니다."
    )
    @GetMapping
    public Response<List<SeniorWithdrawalNoticeResponse>> getUnacknowledgedNotices(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(noticeService.getUnacknowledgedNotices(user));
    }

    @Operation(
            summary = "시니어 탈퇴 알림 확인 처리",
            description = "로그인한 자녀가 시니어 탈퇴 알림을 확인한 것으로 처리합니다. "
                    + "확인 처리된 알림은 미확인 알림 목록과 시니어 목록에서 제외됩니다."
    )
    @PatchMapping("/{noticeId}/acknowledgement")
    public Response<Void> acknowledgeNotice(
            @AuthenticationPrincipal User user,
            @PathVariable Long noticeId
    ) {
        noticeService.acknowledgeNotice(user, noticeId);
        return Response.ok();
    }
}
