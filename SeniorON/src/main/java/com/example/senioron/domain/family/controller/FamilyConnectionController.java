package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.response.FamilyConnectionDisconnectResponse;
import com.example.senioron.domain.family.service.FamilyConnectionService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가족", description = "시니어 코드 등 가족 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/families")
public class FamilyConnectionController {

    private final FamilyConnectionService familyConnectionService;

    @Operation(
            summary = "가족 연결 해제",
            description = "현재 사용자의 선택한 가족 구성원 연결을 해제하고 가족 삭제 여부를 반환합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "가족 연결 해제 성공",
            useReturnTypeSchema = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "가족까지 삭제된 경우",
                                    value = """
                                            {
                                              "status": "OK",
                                              "code": "COMMON_200",
                                              "message": "성공적으로 처리되었습니다.",
                                              "data": {
                                                "familyId": 1,
                                                "familyDeleted": true
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "가족이 유지된 경우",
                                    value = """
                                            {
                                              "status": "OK",
                                              "code": "COMMON_200",
                                              "message": "성공적으로 처리되었습니다.",
                                              "data": {
                                                "familyId": 1,
                                                "familyDeleted": false
                                              }
                                            }
                                            """
                            )
                    }
            )
    )
    @DeleteMapping("/{familyId}/connection")
    @ResponseStatus(HttpStatus.OK)
    public Response<FamilyConnectionDisconnectResponse> disconnect(
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "연결 해제할 가족 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable Long familyId
    ) {
        return Response.ok(
                familyConnectionService.disconnect(user, familyId)
        );
    }
}
