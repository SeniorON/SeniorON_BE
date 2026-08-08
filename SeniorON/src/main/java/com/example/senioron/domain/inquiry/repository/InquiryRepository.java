package com.example.senioron.domain.inquiry.repository;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByUserUsersIdOrderByCreatedAtDescInquiryIdDesc(Long usersId);

    Optional<Inquiry> findByInquiryIdAndUserUsersId(
            Long inquiryId,
            Long usersId
    );
}
