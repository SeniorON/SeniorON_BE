package com.example.senioron.domain.senior.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SeniorRelationMigrationTest {

    @Test
    void migrationAllowsNullRelationAndPreservesExistingRelationColumns() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:senior-relation-migration;MODE=MySQL;DB_CLOSE_DELAY=-1"
        ); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE seniors (
                        senior_id BIGINT PRIMARY KEY,
                        relation VARCHAR(255) NOT NULL,
                        custom_relation VARCHAR(255) NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO seniors (senior_id, relation, custom_relation)
                    VALUES (1, 'MOTHER', '기존 관계')
                    """);

            assertThatThrownBy(() -> insertSenior(connection, 2L, null, null))
                    .isInstanceOf(SQLException.class);

            String migration = new ClassPathResource(
                    "db/migration/V20261105_01__make_seniors_relation_nullable.sql"
            ).getContentAsString(StandardCharsets.UTF_8);
            statement.execute(migration);

            insertSenior(connection, 2L, null, null);

            try (var resultSet = statement.executeQuery("""
                    SELECT relation, custom_relation
                    FROM seniors
                    WHERE senior_id = 1
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("relation")).isEqualTo("MOTHER");
                assertThat(resultSet.getString("custom_relation")).isEqualTo("기존 관계");
            }
        }
    }

    private void insertSenior(
            Connection connection,
            Long seniorId,
            String relation,
            String customRelation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO seniors (senior_id, relation, custom_relation)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, seniorId);
            statement.setString(2, relation);
            statement.setString(3, customRelation);
            statement.executeUpdate();
        }
    }
}
