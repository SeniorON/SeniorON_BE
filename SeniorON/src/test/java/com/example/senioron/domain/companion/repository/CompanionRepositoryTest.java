package com.example.senioron.domain.companion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.companion.crypto.CompanionEncryptionProperties;
import com.example.senioron.domain.companion.crypto.CompanionTextCipher;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionMessage;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class CompanionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanionConversationRepository
            conversationRepository;

    @Autowired
    private CompanionTurnRepository turnRepository;

    @Autowired
    private CompanionMessageRepository messageRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate =
                new JdbcTemplate(dataSource);
    }

    @Test
    void conversationAndRequestIdMustBeUnique() {
        CompanionConversation conversation =
                saveConversation();

        String requestId =
                UUID.randomUUID().toString();

        turnRepository.saveAndFlush(
                CompanionTurn.receive(
                        conversation,
                        requestId
                )
        );

        CompanionTurn duplicate =
                CompanionTurn.receive(
                        conversation,
                        requestId
                );

        assertThatThrownBy(() ->
                turnRepository.saveAndFlush(
                        duplicate
                )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    @Test
    void turnAndMessageRoleMustBeUnique() {
        CompanionTurn turn =
                saveTurn();

        messageRepository.saveAndFlush(
                CompanionMessage.create(
                        turn,
                        MessageRole.USER,
                        "v1:first-iv:first-content"
                )
        );

        CompanionMessage duplicate =
                CompanionMessage.create(
                        turn,
                        MessageRole.USER,
                        "v1:second-iv:second-content"
                );

        assertThatThrownBy(() ->
                messageRepository.saveAndFlush(
                        duplicate
                )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    @Test
    void databaseStoresCiphertextWithoutPlaintext() {
        CompanionTurn turn =
                saveTurn();

        String plaintext =
                "오늘 날씨가 참 좋네요.";

        CompanionTextCipher cipher =
                createCipher();

        CompanionMessage saved =
                messageRepository.saveAndFlush(
                        CompanionMessage.create(
                                turn,
                                MessageRole.USER,
                                cipher.encrypt(plaintext)
                        )
                );

        String rawDatabaseValue =
                jdbcTemplate.queryForObject(
                        """
                        SELECT encrypted_content
                        FROM companion_messages
                        WHERE message_id = ?
                        """,
                        String.class,
                        saved.getMessageId()
                );

        assertThat(rawDatabaseValue)
                .startsWith("v1:");

        assertThat(rawDatabaseValue)
                .doesNotContain(plaintext);

        assertThat(
                cipher.decrypt(rawDatabaseValue)
        ).isEqualTo(plaintext);
    }

    private CompanionTurn saveTurn() {
        CompanionConversation conversation =
                saveConversation();

        return turnRepository.saveAndFlush(
                CompanionTurn.receive(
                        conversation,
                        UUID.randomUUID().toString()
                )
        );
    }

    private CompanionConversation saveConversation() {
        String unique =
                UUID.randomUUID().toString();

        User parent =
                userRepository.saveAndFlush(
                        User.builder()
                                .loginId(
                                        "parent-" + unique
                                )
                                .email(
                                        unique + "@test.com"
                                )
                                .password(
                                        "encoded-password"
                                )
                                .name("테스트 부모")
                                .role(Role.PARENT)
                                .status(
                                        UserStatus.ACTIVE
                                )
                                .build()
                );

        return conversationRepository
                .saveAndFlush(
                        CompanionConversation.start(
                                parent
                        )
                );
    }

    private CompanionTextCipher createCipher() {
        byte[] key = new byte[32];

        Arrays.fill(key, (byte) 1);

        CompanionEncryptionProperties properties =
                new CompanionEncryptionProperties();

        properties.setKey(
                Base64.getEncoder()
                        .encodeToString(key)
        );

        return new CompanionTextCipher(properties);
    }
}