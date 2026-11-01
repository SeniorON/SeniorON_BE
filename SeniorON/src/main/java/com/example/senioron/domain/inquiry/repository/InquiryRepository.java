
package com.example.senioron.domain.inquiry.repository;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByUserUsersIdOrderByCreatedAtDescInquiryIdDesc(
            Long usersId
    );

    Optional<Inquiry> findByInquiryIdAndUserUsersId(
            Long inquiryId,
            Long usersId
    );

    @EntityGraph(attributePaths = {"user"})
    @Query(
            value = """
                    SELECT i
                    FROM Inquiry i
                    JOIN i.user u
                    WHERE (:status IS NULL OR i.status = :status)
                      AND (
                          :keyword IS NULL
                          OR LOWER(i.title) LIKE :keyword
                          OR LOWER(u.name) LIKE :keyword
                      )
                    ORDER BY i.createdAt DESC, i.inquiryId DESC
                    """,
            countQuery = """
                    SELECT COUNT(i)
                    FROM Inquiry i
                    JOIN i.user u
                    WHERE (:status IS NULL OR i.status = :status)
                      AND (
                          :keyword IS NULL
                          OR LOWER(i.title) LIKE :keyword
                          OR LOWER(u.name) LIKE :keyword
                      )
                    """
    )
    Page<Inquiry> findAdminInquiries(
            @Param("status") InquiryStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
