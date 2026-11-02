
package com.example.senioron.domain.inquiry.entity;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "inquiry")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InquiryStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "device_model", length = 255)
    private String deviceModel;

    @Column(name = "os_version", length = 255)
    private String osVersion;

    @Column(name = "app_version", length = 255)
    private String appVersion;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_admin_id")
    private Admin completedByAdmin;

    @OneToMany(
            mappedBy = "inquiry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<InquiryImage> images = new ArrayList<>();

    @OneToMany(
            mappedBy = "inquiry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<InquiryAnswer> answers = new ArrayList<>();

    public void addImage(InquiryImage image) {
        images.add(image);
    }

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = InquiryStatus.WAITING;
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void updateStatus(InquiryStatus status) {
        this.status = status;
    }

    public boolean changeStatus(
            InquiryStatus newStatus,
            Admin admin
    ) {
        if (this.status == newStatus) {
            return false;
        }

        this.status = newStatus;

        if (newStatus == InquiryStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
            this.completedByAdmin = admin;
        } else {
            this.completedAt = null;
            this.completedByAdmin = null;
        }

        return true;
    }
}
