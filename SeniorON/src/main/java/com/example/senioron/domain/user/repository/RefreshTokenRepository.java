package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUserAndDeviceIdentifier(User user, String deviceIdentifier);

    long deleteByExpiresAtBefore(LocalDateTime now);

    void deleteAllByUser(User user);
}
