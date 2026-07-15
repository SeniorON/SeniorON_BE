package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.NameUpdateRequest;
import com.example.senioron.domain.user.dto.request.PasswordChangeRequest;
import com.example.senioron.domain.user.dto.response.CurrentNameResponse;
import com.example.senioron.domain.user.dto.response.NameUpdateResponse;
import com.example.senioron.domain.user.dto.response.PasswordChangeResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserSettingsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public CurrentNameResponse getCurrentName(User principal) {
        validateAuthenticated(principal);

        return CurrentNameResponse.builder()
                .name(principal.getName())
                .build();
    }

    public NameUpdateResponse updateName(User principal, NameUpdateRequest request) {
        validateAuthenticated(principal);

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newName = request.getName().trim();

        if (user.getName().equals(newName)) {
            throw new BusinessException(ErrorCode.NAME_NOT_CHANGED);
        }

        user.updateName(newName);

        return NameUpdateResponse.builder()
                .name(user.getName())
                .build();
    }

    public PasswordChangeResponse changePassword(User principal, PasswordChangeRequest request) {
        validateAuthenticated(principal);

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (!request.getNewPassword().equals(request.getNewPasswordCheck())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));

        return PasswordChangeResponse.builder()
                .changed(true)
                .build();
    }

    private void validateAuthenticated(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }
    }
}
