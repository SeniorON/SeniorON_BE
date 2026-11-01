
package com.example.senioron.domain.inquiry.repository;

import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InquiryAnswerRepository
        extends JpaRepository<InquiryAnswer, Long> {

    Optional<InquiryAnswer> findByInquiryAnswerIdAndInquiry_InquiryId(
            Long answerId,
            Long inquiryId
    );
}
