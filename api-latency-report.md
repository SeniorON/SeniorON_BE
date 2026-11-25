# SeniorON API 응답 속도 측정 리포트

- **측정 일시**: 2026-09-10 10:59:07
- **대상 서버**: `https://senioron.site`
- **측정 모드**: `all` (반복: 3회)
- **전체 API 평균 속도**: **152.2ms**
- **속도 분포**: 🟢 빠름(<150ms): **56개** | 🟡 보통(150~400ms): **3개** | 🔴 병목(>400ms): **3개**

## 🚨 가장 느린 API Top 5 (최적화 우선 대상)

| 순위 | 도메인 | 메서드 | 경로 | 평균(Avg) | P95 | 상태코드 | 비고 |
| :---: | :--- | :---: | :--- | :---: | :---: | :---: | :--- |
| 1 | User (Auth) | `POST` | `/api/users/signup/email/verification-code` | **3633.1ms** | 4293.1ms | `200` | 회원가입 이메일 인증코드 발송 (POST) |
| 2 | System | `GET` | `/v3/api-docs` | **1300.3ms** | 2799.8ms | `200` | Swagger API Docs |
| 3 | Home | `GET` | `/api/home/weather?latitude=37.5665&longitude=126.978` | **717.6ms** | 1492.4ms | `200` | 현재 날씨 조회 |
| 4 | Medication | `PATCH` | `/api/v1/medication-logs/check` | **197.6ms** | 450.2ms | `200` | 가장 가까운 복약 일정 체크 (PATCH) |
| 5 | Family | `GET` | `/api/family/home` | **196.0ms** | 491.1ms | `200` | 가족 홈 요약 조회 |

## 📋 전체 API 응답 속도 상세 결과

| 도메인 | API 설명 | 메서드 | 엔드포인트 | 상태코드 | 평균(Avg) | 최소(Min) | 최대(Max) | P95 |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| User (Auth) | 회원가입 이메일 인증코드 발송 (POST) | `POST` | `/api/users/signup/email/verification-code` | `200` | 🔴 **3633.1ms** | 2934ms | 4293ms | 4293.1ms |
| System | Swagger API Docs | `GET` | `/v3/api-docs` | `200` | 🔴 **1300.3ms** | 272ms | 2800ms | 2799.8ms |
| Home | 현재 날씨 조회 | `GET` | `/api/home/weather?latitude=37.5665&longitude=126.978` | `200` | 🔴 **717.6ms** | 323ms | 1492ms | 1492.4ms |
| Medication | 가장 가까운 복약 일정 체크 (PATCH) | `PATCH` | `/api/v1/medication-logs/check` | `200` | 🟡 **197.6ms** | 69ms | 450ms | 450.2ms |
| Family | 가족 홈 요약 조회 | `GET` | `/api/family/home` | `200` | 🟡 **196.0ms** | 47ms | 491ms | 491.1ms |
| FamilyPhoto | 가족 앨범 목록 조회 | `GET` | `/api/family/photos/albums` | `200` | 🟡 **170.7ms** | 67ms | 364ms | 363.7ms |
| Device | 단말기 위치 업데이트 (PATCH) | `PATCH` | `/api/devices/location` | `204` | 🟢 **146.6ms** | 50ms | 338ms | 338.3ms |
| User (Auth) | 로그인 (POST) | `POST` | `/api/users/login` | `200` | 🟢 **140.9ms** | 137ms | 146ms | 146.0ms |
| Event | SOS 긴급 알림 발생 (POST) | `POST` | `/api/event/sos` | `200` | 🟢 **122.1ms** | 83ms | 175ms | 175.4ms |
| User (Auth) | 온보딩 상태 조회 | `GET` | `/api/users/me/onboarding-status` | `200` | 🟢 **102.5ms** | 53ms | 185ms | 184.5ms |
| Home | 부모님 홈 화면 조회 | `GET` | `/api/home/senior` | `404` | 🟢 **96.4ms** | 52ms | 185ms | 185.3ms |
| Medication | 본인 당일 복약 일정 조회 | `GET` | `/api/v1/medications/schedules?date=2026-09-09` | `200` | 🟢 **85.0ms** | 51ms | 149ms | 149.4ms |
| Home | 홈 메인 화면 조회 | `GET` | `/api/home` | `403` | 🟢 **75.6ms** | 44ms | 98ms | 98.1ms |
| Hospital | 부모님 병원 일정 등록 (POST) | `POST` | `/api/hospitals/parents/1` | `403` | 🟢 **69.3ms** | 59ms | 80ms | 79.9ms |
| User (Auth) | 아이디 중복 확인 | `GET` | `/api/users/check-login-id?loginId=benchmark_check_id` | `200` | 🟢 **64.8ms** | 46ms | 99ms | 98.9ms |
| Event | 이벤트 상세 조회 | `GET` | `/api/event/1` | `200` | 🟢 **64.5ms** | 41ms | 98ms | 97.8ms |
| Notification | 알림 목록 조회 (SOS) | `GET` | `/api/notification?type=SOS&size=20` | `200` | 🟢 **62.0ms** | 51ms | 83ms | 83.3ms |
| Hospital | 부모님 다가오는 병원 일정 조회 | `GET` | `/api/hospitals/parents/1/upcoming` | `200` | 🟢 **60.5ms** | 54ms | 69ms | 69.3ms |
| Device | 단말기 배터리 상태 업데이트 (PUT) | `PUT` | `/api/devices/status` | `204` | 🟢 **59.1ms** | 50ms | 72ms | 72.1ms |
| Medication | 부모님 복약 그룹 수정 (PUT) | `PUT` | `/api/medications/parents/1` | `400` | 🟢 **59.0ms** | 56ms | 64ms | 64.1ms |
| Medication | 부모님 복약 그룹 목록 조회 | `GET` | `/api/medications/parents/1` | `200` | 🟢 **58.7ms** | 48ms | 78ms | 78.1ms |
| Inquiry | 문의사항 상세 조회 | `GET` | `/api/inquiries/1` | `404` | 🟢 **58.5ms** | 54ms | 66ms | 66.3ms |
| Device | 단말기 최근 위치 조회 | `GET` | `/api/devices/location` | `403` | 🟢 **57.0ms** | 45ms | 72ms | 72.0ms |
| Home | 오늘의 병원 일정 요약 조회 | `GET` | `/api/home/hospitals/today` | `200` | 🟢 **55.8ms** | 44ms | 77ms | 77.1ms |
| Event | 위험 감지 링크 이벤트 전송 (POST) | `POST` | `/api/event/risk-link` | `400` | 🟢 **55.1ms** | 42ms | 75ms | 74.7ms |
| FamilyPhoto | 가족사진 상세 조회 | `GET` | `/api/family/photos/1` | `404` | 🟢 **55.0ms** | 45ms | 71ms | 71.1ms |
| Inquiry | 문의사항 목록 조회 | `GET` | `/api/inquiries?page=0&size=10` | `200` | 🟢 **54.7ms** | 46ms | 65ms | 64.9ms |
| Inactivity | 본인 미활동 설정 조회 | `GET` | `/api/inactivity-settings/me` | `200` | 🟢 **54.6ms** | 45ms | 72ms | 71.7ms |
| Medication | 부모님 월간 복약 달력 조회 | `GET` | `/api/v1/medications/parents/1/schedules/monthly?year=2026&month=9` | `403` | 🟢 **53.8ms** | 45ms | 64ms | 64.2ms |
| FamilyPhoto | 가족사진 목록 조회 | `GET` | `/api/family/photos?page=0&size=10` | `200` | 🟢 **53.3ms** | 48ms | 60ms | 59.9ms |
| Notification | 알림 수신 설정 조회 | `GET` | `/api/notification/setting` | `403` | 🟢 **52.1ms** | 51ms | 55ms | 54.9ms |
| Hospital | 부모님 병원 월간 일정 조회 | `GET` | `/api/hospitals/parents/1?year=2026&month=9` | `200` | 🟢 **50.6ms** | 49ms | 52ms | 51.5ms |
| Medication | 부모님 복약 그룹 등록 (POST) | `POST` | `/api/medications/parents/1` | `403` | 🟢 **50.4ms** | 48ms | 55ms | 54.8ms |
| User (Auth) | 회원가입 (POST, 매 실행마다 계정 생성됨) | `POST` | `/api/users/signup` | `400` | 🟢 **49.9ms** | 42ms | 55ms | 55.1ms |
| Home | 홈 버튼 배치 저장 (PUT) | `PUT` | `/api/home/buttons` | `403` | 🟢 **49.4ms** | 40ms | 54ms | 54.4ms |
| Home | 홈 버튼 옵션 목록 조회 | `GET` | `/api/home/button-options` | `403` | 🟢 **49.4ms** | 46ms | 52ms | 51.9ms |
| Hospital | 부모님 특정일 병원 일정 조회 | `GET` | `/api/hospitals/parents/1/daily?date=2026-09-09` | `200` | 🟢 **48.8ms** | 45ms | 55ms | 54.5ms |
| Family | 가족 초대 코드 조회 | `GET` | `/api/family/code` | `200` | 🟢 **48.3ms** | 41ms | 60ms | 60.1ms |
| Event | 미활동 이벤트 전송 (POST) | `POST` | `/api/event/inactivity` | `400` | 🟢 **48.0ms** | 38ms | 55ms | 54.8ms |
| Medication | 부모님 당일 복약 스케줄 조회 | `GET` | `/api/v1/medications/parents/1/schedules?date=2026-09-09` | `403` | 🟢 **47.8ms** | 45ms | 50ms | 50.0ms |
| Device | 단말기 FCM 토큰 갱신 (PATCH) | `PATCH` | `/api/devices/fcm-token` | `204` | 🟢 **47.5ms** | 45ms | 51ms | 51.2ms |
| FamilyPhoto | 가족사진 열람 처리 (PATCH) | `PATCH` | `/api/family/photos/1/viewed` | `404` | 🟢 **47.1ms** | 43ms | 54ms | 54.0ms |
| Inactivity | 대상 부모님 미활동 설정 조회 | `GET` | `/api/inactivity-settings/1` | `403` | 🟢 **46.8ms** | 44ms | 51ms | 50.7ms |
| Device | 홈(집) 등록 위치 조회 | `GET` | `/api/devices/home-location` | `404` | 🟢 **46.7ms** | 41ms | 56ms | 56.4ms |
| User (Auth) | 로그아웃 (POST) | `POST` | `/api/users/logout` | `400` | 🟢 **46.2ms** | 40ms | 50ms | 49.9ms |
| FamilyPhoto | 가족사진 업로드 완료 처리 (POST) | `POST` | `/api/family/photos/complete` | `400` | 🟢 **45.5ms** | 39ms | 55ms | 54.8ms |
| Notification | 부모님 단말기 배터리/연결 상태 조회 | `GET` | `/api/notification/parent-device-status` | `403` | 🟢 **44.7ms** | 42ms | 51ms | 50.7ms |
| Notification | 알림 읽음 처리 (PATCH) | `PATCH` | `/api/notification/1/read` | `404` | 🟢 **44.6ms** | 41ms | 47ms | 47.5ms |
| Family | 가족 구성원 목록 조회 | `GET` | `/api/family/members` | `200` | 🟢 **44.1ms** | 39ms | 50ms | 49.9ms |
| Home | 글자 크기 설정 변경 (PATCH) | `PATCH` | `/api/home/font-size` | `403` | 🟢 **44.0ms** | 41ms | 48ms | 48.2ms |
| Family | 가족 초대 코드 생성 (POST) | `POST` | `/api/family/code-create` | `403` | 🟢 **43.8ms** | 43ms | 45ms | 44.5ms |
| User (Auth) | 회원가입 이메일 인증코드 확인 (POST) | `POST` | `/api/users/signup/email/verification-code/verify` | `400` | 🟢 **43.8ms** | 37ms | 50ms | 49.9ms |
| FamilyPhoto | 가족사진 업로드 URL 발급 (POST) | `POST` | `/api/family/photos/upload-url` | `400` | 🟢 **43.3ms** | 35ms | 53ms | 53.0ms |
| Event | 외출/귀가 이벤트 전송 (POST) | `POST` | `/api/event/outing-return` | `400` | 🟢 **42.9ms** | 40ms | 45ms | 44.6ms |
| Inactivity | 미활동 감지 임계시간 변경 (PATCH) | `PATCH` | `/api/inactivity-settings/1` | `403` | 🟢 **42.9ms** | 39ms | 46ms | 45.8ms |
| User (Auth) | 토큰 재발급 (POST) | `POST` | `/api/users/token/refresh` | `401` | 🟢 **42.5ms** | 38ms | 45ms | 45.3ms |
| System | Health Check (Readiness) | `GET` | `/actuator/health/readiness` | `200` | 🟢 **41.9ms** | 32ms | 61ms | 60.9ms |
| Family | 가족 참여 (POST) | `POST` | `/api/family/join` | `400` | 🟢 **41.5ms** | 40ms | 43ms | 42.6ms |
| Family | 가족 대표 관리자 변경 (PATCH) | `PATCH` | `/api/family/primary-manager` | `403` | 🟢 **41.1ms** | 36ms | 47ms | 47.5ms |
| Notification | 알림 수신 설정 토글 (PATCH) | `PATCH` | `/api/notification/setting/INACTIVITY` | `403` | 🟢 **40.8ms** | 40ms | 41ms | 41.3ms |
| Medication | 특정 복약 일정 체크 (PATCH) | `PATCH` | `/api/v1/medication-logs/1/check` | `200` | 🟢 **40.6ms** | 39ms | 42ms | 42.4ms |
| Home | 단말기 상태 상세 조회 | `GET` | `/api/home/device` | `403` | 🟢 **39.3ms** | 36ms | 43ms | 42.8ms |

---
*Generated automatically by SeniorON API Latency Benchmark Tool (Python)*
