package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.service.FamilyConnectionService;
import com.example.senioron.domain.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가족", description = "시니어 코드 등 가족 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/families")
public class FamilyConnectionController {

    private final FamilyConnectionService familyConnectionService;

    @Operation(
            summary = "가족 연결 해제",
            description = "현재 사용자의 선택한 가족 구성원 연결을 해제합니다."
    )
    @DeleteMapping("/{familyId}/connection")
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal User user,
            @PathVariable Long familyId
    ) {
        familyConnectionService.disconnect(user, familyId);
        return ResponseEntity.noContent().build();
    }
}
