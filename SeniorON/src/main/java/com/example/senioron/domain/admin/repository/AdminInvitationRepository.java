package com.example.senioron.domain.admin.repository;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminInvitation;
import com.example.senioron.domain.admin.entity.AdminInvitationType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, Long> {
    Optional<AdminInvitation> findByTokenHash(String tokenHash);

    List<AdminInvitation> findAllByAdminAndType(Admin admin, AdminInvitationType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from AdminInvitation invitation join fetch invitation.admin where invitation.tokenHash = :tokenHash")
    Optional<AdminInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
