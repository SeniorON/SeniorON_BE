package com.example.senioron.domain.companion.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "companion_conversations",
        indexes = {
                @Index(
                        name = "idx_companion_conversation_user_status",
                        columnList = "users_id,status"
                ),
                @Index(
                        name = "idx_companion_conversation_user_created",
                        columnList = "users_id,created_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CompanionConversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Version
    private Long version;

    public static CompanionConversation start(User user) {
        if (user == null) {
            throw new IllegalArgumentException("대화자는 null일 수 없습니다.");
        }

        return CompanionConversation.builder()
                .user(user)
                .status(ConversationStatus.ACTIVE)
                .build();
    }

    public void end() {
        if (status == ConversationStatus.ENDED) {
            return;
        }

        status = ConversationStatus.ENDED;
        endedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean isOwnedBy(Long userId) {
        return user != null
                && userId != null
                && user.getUsersId().equals(userId);
    }
}
