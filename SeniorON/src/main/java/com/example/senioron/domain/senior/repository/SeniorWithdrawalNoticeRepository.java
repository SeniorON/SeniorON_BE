package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorWithdrawalNoticeRepository
        extends JpaRepository<SeniorWithdrawalNotice, Long> {

    Optional<SeniorWithdrawalNotice> findBySeniorId(Long seniorId);
}
