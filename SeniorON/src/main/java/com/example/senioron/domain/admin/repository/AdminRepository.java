package com.example.senioron.domain.admin.repository;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from Admin admin where admin.email = :email")
    Optional<Admin> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from Admin admin where admin.adminId = :adminId")
    Optional<Admin> findByIdForUpdate(@Param("adminId") Long adminId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select admin from Admin admin
            where admin.adminRole = :adminRole and admin.status = :status
            order by admin.adminId asc
            """)
    List<Admin> findAllByAdminRoleAndStatusForUpdate(
            @Param("adminRole") AdminRole adminRole,
            @Param("status") AdminStatus status
    );

    @Query("""
            select admin from Admin admin
            where lower(admin.name) like :pattern escape '\\'
               or lower(admin.email) like :pattern escape '\\'
            """)
    Page<Admin> searchByNameOrEmail(@Param("pattern") String pattern, Pageable pageable);
}
