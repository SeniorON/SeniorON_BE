package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.response.FamilyCodeCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyJoinResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FamilyService {

    private final FamilyRepository familyRepository;

    // 가족 생성 및 공유코드 발급 서비스
    public FamilyCodeCreateResponse createFamily(User user) {
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String familyCode = generateUniqueFamilyCode();

        Family family = Family.builder()
                .familyCode(familyCode)
                .build();

        Family savedFamily = familyRepository.save(family);

        user.updateFamily(savedFamily);

        return FamilyCodeCreateResponse.builder()
                .familyId(savedFamily.getFamilyId())
                .familyCode(savedFamily.getFamilyCode())
                .build();
    }

    // 중복되지 않는 가족 공유코드 생성
    private String generateUniqueFamilyCode() {
        String code;

        do {
            code = generateFamilyCode();
        } while (familyRepository.existsByFamilyCode(code));

        return code;
    }

    // 랜덤 가족 공유코드 생성
    private String generateFamilyCode() {
        String raw = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return raw.substring(0, 4) + "-" + raw.substring(4, 8);
    }

    // 공유코드로 가족 참여 메소드
    public FamilyJoinResponse joinFamily(User user, FamilyJoinRequest request) {
        Family family = familyRepository.findByFamilyCode(request.getFamilyCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FAMILY_CODE));

        user.updateFamily(family);

        return FamilyJoinResponse.builder()
                .familyId(family.getFamilyId())
                .familyCode(family.getFamilyCode())
                .build();
    }
}