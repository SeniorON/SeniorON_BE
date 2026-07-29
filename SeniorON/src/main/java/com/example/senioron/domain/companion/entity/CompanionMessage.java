package com.example.senioron.domain.companion.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "companion_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_companion_message_turn_role",
                        columnNames = {
                                "turn_id",
                                "role"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_companion_message_conversation_message",
                        columnList = "conversation_id,message_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CompanionMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private CompanionConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    private CompanionTurn turn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Lob
    @Column(name = "encrypted_content", nullable = false)
    private String encryptedContent;

    public static CompanionMessage create(
            CompanionTurn turn,
            MessageRole role,
            String encryptedContent
    ) {
        if (turn == null) {
            throw new IllegalArgumentException(
                    "턴 정보는 비어 있을 수 없습니다."
            );
        }
        if (role == null) {
            throw new IllegalArgumentException(
                    "메시지 역할은 비어 있을 수 없습니다."
            );
        }
        if (encryptedContent == null
                || encryptedContent.isBlank()) {
            throw new IllegalArgumentException(
                    "암호화된 내용은 비어 있을 수 없습니다."
            );
        }
        return CompanionMessage.builder()
                .conversation(turn.getConversation())
                .turn(turn)
                .role(role)
                .encryptedContent(encryptedContent)
                .build();
    }
}
