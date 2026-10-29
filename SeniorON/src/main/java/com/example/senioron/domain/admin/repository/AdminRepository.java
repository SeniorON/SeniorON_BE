package com.example.senioron.domain.admin.repository;
import com.example.senioron.domain.admin.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from Admin admin where admin.adminId = :adminId")
    Optional<Admin> findByIdForUpdate(@Param("adminId") Long adminId);
}
