package com.example.senioron.domain.admin.repository;
import com.example.senioron.domain.admin.entity.AdminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> { }
