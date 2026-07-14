package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.LoginIdFindRequest;
import com.example.senioron.domain.user.dto.response.LoginIdFindResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRecoveryService {

    private final UserRepository userRepository;

    public LoginIdFindResponse findLoginId(LoginIdFindRequest request) {
        User user = userRepository.findByNameAndEmail(request.getName(), request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_ID_NOT_FOUND));

        return LoginIdFindResponse.builder()
                .loginId(user.getLoginId())
                .build();
    }
}
