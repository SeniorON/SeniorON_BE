
package com.example.senioron.domain.inquiry.repository;

import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryAnswerRepository
        extends JpaRepository<InquiryAnswer, Long> {
}
