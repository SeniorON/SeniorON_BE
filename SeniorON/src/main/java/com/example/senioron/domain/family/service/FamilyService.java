package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.response.FamilyCodeCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyJoinResponse;
import com.example.senioron.domain.family.dto.response.FamilyMemberResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    // 가족 생성 및 공유코드 발급 서비스
    public FamilyCodeCreateResponse createFamily(User principal) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

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
    public FamilyJoinResponse joinFamily(
            User principal,
            FamilyJoinRequest request
    ) {
        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Family family = familyRepository.findByFamilyCode(request.getFamilyCode())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_FAMILY_CODE)
                );

        user.updateFamily(family);

        return FamilyJoinResponse.builder()
                .familyId(family.getFamilyId())
                .familyCode(family.getFamilyCode())
                .build();
    }

    // 가족 구성원 조회 메소드
    @Transactional(readOnly=true)
    public List<FamilyMemberResponse> getFamilyMembers(User user){
        Family family = user.getFamily();

        if(family == null){
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        return userRepository.findAllByFamily(family).stream()
                // 계정 주인만 맨 앞 정렬
                .sorted(Comparator.comparing(
                        member -> !Objects.equals(member.getUsersId(),user.getUsersId())
                ))
                .map(member -> FamilyMemberResponse.builder()
                        .usersId(member.getUsersId())
                        .name(member.getName())
                        .managerType(member.getManagerType())
                        .me(Objects.equals(member.getUsersId(),user.getUsersId()))
                        .build())
                .toList();
    }

}