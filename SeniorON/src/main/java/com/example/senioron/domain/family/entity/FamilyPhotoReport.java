package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "family_photo_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_family_photo_report_photo_reporter",
                        columnNames = {
                                "family_photo_id",
                                "reporter_user_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_family_photo_report_reporter_hidden_photo",
                        columnList = "reporter_user_id, hidden, family_photo_id"
                ),
                @Index(
                        name = "idx_family_photo_report_status_created",
                        columnList = "status, created_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyPhotoReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_photo_report_id")
    private Long id;

    // 사진이 삭제돼도 신고 기록은 남긴다 (마이그레이션의 ON DELETE SET NULL과 동일)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_photo_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FamilyPhoto familyPhoto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reporter_user_id",
            nullable = false
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User reporter;

    // 사진이 삭제돼도 관리자가 업로더를 확인할 수 있도록 신고 시점의 업로더를 남긴다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reportedUser;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private PhotoReportReason reason;

    @Column(name = "detail", length = 200)
    private String detail;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PhotoReportStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;
}