# 🚀 SeniorON API 응답 속도 종합 측정 도구

SeniorON 백엔드의 모든 API 응답 속도를 한 번에 측정하고, 병목이 발생하는 API를 식별하여 리포트(Markdown / Console)를 생성하는 도구입니다.

---

## 📌 주요 특징

1. **원클릭 전수 측정**: 17개 도메인(Home, Family, Hospital, Medication, User 등)의 모든 주요 API를 일괄 호출하여 속도를 측정합니다.
2. **자동 JWT 인증 주입**: 로그인 아이디/비밀번호만 전달하면 JWT 토큰을 자동 발급받아 인증이 필요한 모든 API 헤더(`Authorization: Bearer <token>`)에 주입합니다.
3. **안전 모드 (`--mode safe`) 기본 제공**:
   - DB 데이터를 훼손하지 않는 GET/조회성 API를 우선 안전하게 전수 측정합니다.
   - `--mode all` 옵션을 주면 쓰기/수정(POST/PUT/PATCH) API까지 포함하여 측정할 수 있습니다.
4. **정밀 통계 (Min, Avg, Max, P95)**:
   - 각 API를 N회(기본 3회) 반복 호출하여 네트워크 튐 현상을 보정하고 P95 및 평균 응답 시간을 산출합니다.
5. **결과 리포트 자동 생성**:
   - 콘솔에 실시간 컬러 테이블 출력
   - 가장 느린 API Top 5 자동 하이라이트
   - `api-latency-report.md` 파일로 저장

---

## 🛠️ 실행 방법

별도의 `npm install` 없이 Node.js (v18+ / v26) 내장 `fetch`로 즉시 실행됩니다.

### 1. 로컬 개발 환경 (`http://localhost:8080`)

```bash
# 1) 테스트 계정으로 자동 로그인하여 측정 (권장)
node tools/api-latency-tester/measure-all-apis.js \
  --url http://localhost:8080 \
  --user testuser \
  --password "Password123!"

# 2) 이미 발급받은 JWT 토큰을 직접 사용하는 경우
node tools/api-latency-tester/measure-all-apis.js \
  --url http://localhost:8080 \
  --token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 2. 배포 서버 (`https://senioron.site`)

```bash
node tools/api-latency-tester/measure-all-apis.js \
  --url https://senioron.site \
  --user testuser \
  --password "Password123!"
```

### 3. 전체 모드 (POST/PUT/PATCH 등 CUD 포함)

```bash
node tools/api-latency-tester/measure-all-apis.js \
  --url http://localhost:8080 \
  --mode all \
  --user testuser \
  --password "Password123!" \
  --iterations 5
```

---

## ⚙️ CLI 옵션 가이드

| 옵션 | 기본값 | 설명 |
| :--- | :--- | :--- |
| `--url` | `http://localhost:8080` | 테스트할 백엔드 서버 베이스 주소 |
| `--user` | - | 자동 로그인할 사용자 아이디 (`/api/users/login`) |
| `--password` | - | 사용자 비밀번호 |
| `--token` | - | 직접 전달할 JWT Bearer 토큰 (지정 시 로그인 생략) |
| `--mode` | `safe` | `safe`: 조회(GET) 위주 안전 측정<br>`all`: CUD 포함 전체 측정 |
| `--iterations` | `3` | 각 API별 반복 측정 횟수 |
| `--delay` | `100` | 요청 간 대기 시간 (ms) |
| `--output` | `api-latency-report.md` | 마크다운 결과 파일 경로 |

---

## 🔍 서버 내부 응답 속도 확인 방법 (추가 팁)

외부 클라이언트 측정뿐만 아니라, 백엔드 내부에서도 이미 측정 로직이 동작하고 있습니다.

### 1. `ApiLoggingFilter` 실시간 로그 확인
SeniorON 백엔드는 모든 요청에 대해 `ApiLoggingFilter`를 통해 처리 시간을 밀리초 단위로 기록하고 있습니다:
```text
[PERF] GET /api/home - request total=42ms jwtAuthentication=4ms controller/service after JWT=38ms status=200
```
- 터미널에서 아래 명령어로 실시간 병목 API만 필터링해서 볼 수 있습니다:
```bash
# 100ms 이상 소요된 요청만 모니터링
grep "\[PERF\]" <서버로그파일>
```

### 2. Prometheus & Grafana 메트릭 확인
Spring Boot Actuator Prometheus를 통해 모든 엔드포인트의 응답 속도가 이미 수집되고 있습니다:
- **평균 응답 속도 (PromQL)**:
  ```promql
  rate(http_server_requests_seconds_sum{job="spring-blue"}[5m]) 
  / 
  rate(http_server_requests_seconds_count{job="spring-blue"}[5m])
  ```
- **최대 응답 지연 (PromQL)**:
  ```promql
  http_server_requests_seconds_max{job="spring-blue"}
  ```
