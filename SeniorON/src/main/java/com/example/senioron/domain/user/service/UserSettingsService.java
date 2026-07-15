package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.NameUpdateRequest;
import com.example.senioron.domain.user.dto.response.CurrentNameResponse;
import com.example.senioron.domain.user.dto.response.NameUpdateResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserSettingsService {

    private final UserRepository userRepository;

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

    private void validateAuthenticated(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }
    }
}
