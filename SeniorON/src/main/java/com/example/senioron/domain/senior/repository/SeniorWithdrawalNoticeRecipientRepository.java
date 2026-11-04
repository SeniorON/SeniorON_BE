package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorWithdrawalNoticeRecipientRepository
        extends JpaRepository<SeniorWithdrawalNoticeRecipient, Long> {
}
