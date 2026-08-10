CREATE TABLE IF NOT EXISTS companion_conversations (
    conversation_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    status ENUM('ACTIVE', 'ENDED') NOT NULL,
    version BIGINT NOT NULL,
    users_id BIGINT NOT NULL,

    PRIMARY KEY (conversation_id),

    INDEX idx_companion_conversation_user_status (
        users_id,
        status
    ),

    INDEX idx_companion_conversation_user_created (
        users_id,
        created_at
    ),

    CONSTRAINT fk_companion_conversations_users
        FOREIGN KEY (users_id)
            REFERENCES users (users_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


CREATE TABLE IF NOT EXISTS companion_turns (
    turn_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    failure_stage ENUM(
        'LLM',
        'PERSISTENCE',
        'SAFETY',
        'STT',
        'TTS'
    ) NULL,
    input_tokens INT NULL,
    llm_model VARCHAR(255) NULL,
    llm_provider VARCHAR(255) NULL,
    output_tokens INT NULL,
    prompt_version VARCHAR(255) NULL,
    request_id VARCHAR(36) NOT NULL,
    safety_rule_id VARCHAR(255) NULL,
    safety_type ENUM(
        'EMERGENCY',
        'NORMAL'
    ) NULL,
    status ENUM(
        'COMPLETED',
        'FAILED',
        'RECEIVED',
        'RESPONSE_GENERATED',
        'TRANSCRIBED'
    ) NOT NULL,
    stt_model VARCHAR(255) NULL,
    stt_provider VARCHAR(255) NULL,
    tts_provider VARCHAR(255) NULL,
    tts_voice VARCHAR(255) NULL,
    conversation_id BIGINT NOT NULL,

    PRIMARY KEY (turn_id),

    UNIQUE KEY uk_companion_turn_conversation_request (
        conversation_id,
        request_id
    ),

    INDEX idx_companion_turn_conversation_created (
        conversation_id,
        created_at
    ),

    INDEX idx_companion_turn_conversation_status (
        conversation_id,
        status
    ),

    CONSTRAINT fk_companion_turns_conversation
        FOREIGN KEY (conversation_id)
            REFERENCES companion_conversations (
                conversation_id
            )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


CREATE TABLE IF NOT EXISTS companion_messages (
    message_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    encrypted_content LONGTEXT NOT NULL,
    role ENUM(
        'ASSISTANT',
        'USER'
    ) NOT NULL,
    conversation_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,

    PRIMARY KEY (message_id),

    UNIQUE KEY uk_companion_message_turn_role (
        turn_id,
        role
    ),

    INDEX idx_companion_message_conversation_message (
        conversation_id,
        message_id
    ),

    CONSTRAINT fk_companion_messages_conversation
        FOREIGN KEY (conversation_id)
            REFERENCES companion_conversations (
                conversation_id
            ),

    CONSTRAINT fk_companion_messages_turn
        FOREIGN KEY (turn_id)
            REFERENCES companion_turns (
                turn_id
            )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


ALTER TABLE companion_messages
    MODIFY COLUMN encrypted_content
        LONGTEXT NOT NULL;


CREATE INDEX IF NOT EXISTS
    idx_companion_turn_conversation_status
    ON companion_turns (
        conversation_id,
        status
    );