# 알림 메인화면 WebSocket 연동

SOS·무활동·위험링크·외출/귀가 알림이 저장되면 해당 알림을 받은 보호자의 **알림 메인화면**을 갱신한다. 알림 상세·기록 목록의 실시간 갱신은 이번 연동 범위가 아니다. 기존 FCM과 REST API는 그대로 사용한다.

## 연결 및 인증

- 로컬: `ws://localhost:8080/ws` (실제 서버 포트에 맞춰 변경)
- 배포: `wss://senioron.site/ws`
- 프로토콜: STOMP 1.2 over WebSocket. SockJS endpoint는 제공하지 않는다.
- STOMP `CONNECT` native header: `Authorization: Bearer <accessToken>`
- HTTP handshake의 Authorization 헤더로 인증하는 기존 네이티브 클라이언트도 지원한다.
- refresh token, 만료/위조 토큰, 탈퇴 계정, 인증 없는 연결은 거절한다.
- JWT는 연결 시 검증한다. 앱은 토큰 갱신·계정 변경 시 기존 연결을 닫고 새 access token으로 재연결한다.
- 자녀(CHILD)는 `/user/queue/notification-home`을 구독한다. 경로에 사용자 ID나 seniorId를 붙이지 않는다.
- `/queue` 직접 구독, 다른 사용자의 채널, 와일드카드 구독 및 클라이언트의 `SEND`는 허용하지 않는다.
- 기존 `/topic/senior/{parentUsersId}/home` 채널은 기존 사용자 ID 계약을 유지한다.

## 수신 메시지

```json
{
  "action": "NOTIFICATION_HOME_UPDATED",
  "seniorId": 3,
  "notificationId": 41,
  "eventId": 15,
  "type": "SOS",
  "reason": "CREATED"
}
```

- `type`: `SOS`, `INACTIVITY`, `RISK_LINK`, `OUTING_RETURN`
- `reason`: `CREATED` 또는 `ADDRESS_UPDATED`
- `seniorId`: Senior 엔티티 ID. 부모 계정 usersId와 구분한다.
- 알림 저장 트랜잭션이 커밋된 뒤 전송한다. 롤백되면 전송하지 않는다.
- FCM 토큰이 없어도 Notification이 저장됐다면 신호를 전송한다. FCM 응답의 `NO_DEVICE_TOKEN`은 WebSocket 갱신 실패를 뜻하지 않는다.
- 알림 설정 비활성화 등으로 Notification 자체가 생성되지 않으면 신호도 없다.
- SOS의 주소는 나중에 갱신될 수 있다. 주소 저장 완료 후 실제 수신자 중 여전히 해당 가족에 소속된 보호자에게 `ADDRESS_UPDATED`를 보낸다.

## 앱 처리 순서

1. 알림 메인 진입 시 연결·구독하고 선택된 시니어의 `GET /api/notification/setting?seniorId={seniorId}`를 조회한다.
2. 메시지의 seniorId가 현재 선택한 시니어와 같고 메인화면이 활성 상태이면 같은 API를 재조회해 카드를 갱신한다.
3. 다른 시니어의 신호가 현재 카드를 덮어쓰지 않도록 한다. 시니어 선택이 바뀌면 해당 시니어의 API를 조회한다.
4. 짧은 시간에 여러 신호가 도착하면 재조회를 합치되, 요청 도중 도착한 신호는 후속 재조회로 처리한다. 이전 시니어의 늦은 응답은 버린다.
5. 화면 재진입·앱 포그라운드 복귀·연결 복구 시 구독 후 API를 다시 조회한다. 오프라인 중의 신호는 재생되지 않는다.
6. 로그아웃 시 구독과 연결을 해제한다. 백그라운드 시스템 알림은 기존 FCM으로 처리한다.

기존 무활동 설정 `/api/inactivity-settings/{targetUserId}`는 부모 usersId를 받는 별도 API이므로 이 메시지의 seniorId를 그대로 넣지 않는다.

## 배포 조건 및 검증 범위

기존 Nginx `/ws` upgrade 설정을 사용한다. 현재 Spring simple broker는 서버 프로세스 메모리에 구독을 보관하므로 단일 활성 인스턴스를 전제로 한다. 여러 인스턴스에서 이벤트를 처리하거나 Blue-Green 전환 중 구 연결을 유지하면 인스턴스 간 갱신 신호가 전달되지 않을 수 있다. 전환 시 재연결·REST 재조회가 필요하며, 다중 활성 인스턴스 운영에는 공유 메시지 브로커를 별도로 적용해야 한다.

서버 테스트는 실제 로컬 WebSocket 연결, JWT/STOMP 인증, 수신 사용자 격리, 다중 시니어 구분, 커밋/롤백, 토큰 없는 수신자, 주소 갱신을 검증한다. Android 화면 처리는 앱 저장소에서 이 계약에 맞게 연결해야 한다.
