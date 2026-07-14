package com.example.senioron.domain.socialaccount.repository;

import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository
        extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderId(
            LoginProvider provider,
            String providerId
    );

    Optional<SocialAccount> findByUserAndProvider(
            User user,
            LoginProvider provider
    );

    boolean existsByProviderAndProviderId(
            LoginProvider provider,
            String providerId
    );
}
