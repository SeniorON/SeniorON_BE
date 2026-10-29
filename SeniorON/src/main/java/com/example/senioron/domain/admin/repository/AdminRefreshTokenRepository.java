package com.example.senioron.domain.admin.repository;
import com.example.senioron.domain.admin.entity.AdminRefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, Long> {
    Optional<AdminRefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AdminRefreshToken token where token.tokenHash = :tokenHash")
    Optional<AdminRefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    long deleteByExpiresAtBeforeOrRevokedTrue(LocalDateTime now);
}
