
package com.example.senioron.domain.inquiry.repository;

import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryAnswerRepository
        extends JpaRepository<InquiryAnswer, Long> {

    Optional<InquiryAnswer> findByInquiryAnswerIdAndInquiry_InquiryId(
            Long answerId,
            Long inquiryId
    );

    boolean existsByInquiry_InquiryId(Long inquiryId);
}
