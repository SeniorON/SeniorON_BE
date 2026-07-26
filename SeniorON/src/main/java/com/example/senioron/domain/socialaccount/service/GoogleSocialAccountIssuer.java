package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleSocialAccountIssuer {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result findOrCreate(String providerId, String email, String name) {
        return socialAccountRepository.findByProviderAndProviderId(LoginProvider.GOOGLE, providerId)
                .map(socialAccount -> new Result(initializeUser(socialAccount), false))
                .orElseGet(() -> new Result(create(providerId, email, name), true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result findExisting(String providerId) {
        SocialAccount socialAccount =
                socialAccountRepository.findByProviderAndProviderId(LoginProvider.GOOGLE, providerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GOOGLE_LOGIN_CONFLICT));

        return new Result(initializeUser(socialAccount), false);
    }

    private SocialAccount create(String providerId, String email, String name) {
        User user = userRepository.saveAndFlush(
                User.builder()
                        .email(email)
                        .name(name)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        return socialAccountRepository.saveAndFlush(
                SocialAccount.builder()
                        .user(user)
                        .provider(LoginProvider.GOOGLE)
                        .providerId(providerId)
                        .build()
        );
    }

    private SocialAccount initializeUser(SocialAccount socialAccount) {
        socialAccount.getUser().getUsersId();
        return socialAccount;
    }

    public record Result(
            SocialAccount socialAccount,
            boolean newUser
    ) {
    }
}
