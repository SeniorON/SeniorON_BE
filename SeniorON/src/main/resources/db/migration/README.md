# Flyway Migration Guide

운영 DB 스키마 변경은 엔티티 수정과 함께 새 migration SQL 파일로 배포한다.

## 파일명 규칙

이미 배포된 migration 파일은 수정하지 않는다.
새 변경은 항상 더 높은 버전의 새 파일을 만든다.

예시:

```text
V20260801_01__add_profile_image_index.sql
V20260801_02__add_user_status_column.sql
V20260802_01__change_notification_body_length.sql
```

형식:

```text
V{yyyyMMdd}_{순번}__{설명}.sql
```

## 예시

엔티티에 컬럼을 추가했다면:

```java
@Column(name = "nickname")
private String nickname;
```

같은 PR에 migration 파일도 추가한다:

```sql
ALTER TABLE users
    ADD COLUMN nickname VARCHAR(255);
```

인덱스를 추가했다면:

```java
@Index(name = "idx_users_status", columnList = "status")
```

같은 PR에 migration 파일도 추가한다:

```sql
CREATE INDEX IF NOT EXISTS idx_users_status
    ON users (status);
```

## 주의

- Flyway는 엔티티 변경을 자동으로 SQL로 만들어주지 않는다.
- `ddl-auto: validate`는 DB를 수정하지 않고 엔티티와 DB가 맞는지만 검사한다.
- migration SQL이 빠지면 운영 배포 시 앱 시작이 실패할 수 있다.
- 이미 운영에 적용된 migration 파일은 수정하지 말고, 새 파일을 추가한다.
