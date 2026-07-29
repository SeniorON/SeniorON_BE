# FCM 테스트 토큰 발급 도구

FCM 푸시 발송을 실제로 테스트하려면 유효한 기기(브라우저) 토큰이 필요합니다. FCM 토큰은
백엔드가 발급할 수 없고 클라이언트(Firebase SDK)가 생성하는 값인데, 아직 `SeniorON_AOS`
안드로이드 앱에는 알림/Firebase 연동이 붙지 않은 상태입니다. 그래서 Android Studio 없이
브라우저(Web Push)로 진짜 토큰을 받을 수 있는 최소 페이지를 만들어뒀습니다.

## 준비

1. `firebase-config.example.js`를 `firebase-config.js`로 복사합니다.
   ```
   cp firebase-config.example.js firebase-config.js
   ```
   `firebase-config.js`는 `.gitignore`에 등록되어 있어 커밋되지 않습니다.
2. `firebase-config.js` 안의 값을 채웁니다. 이 값들은 팀 전체가 공통으로 쓰는 프로젝트
   식별자라(개인별로 다르지 않음), 이미 값을 채운 팀원에게 직접 공유받아도 되고,
   Firebase 콘솔에서 직접 가져와도 됩니다.
   - `apiKey`, `messagingSenderId`, `appId` 등:
     콘솔 → 프로젝트 설정(⚙️) → 일반 → "내 앱" → 웹 앱(`</>`) → SDK 설정 및 구성
   - `VAPID_KEY`:
     콘솔 → 프로젝트 설정 → Cloud Messaging → "웹 구성" 탭 → Web Push 인증서 → 키 쌍 생성

## 실행

Service Worker는 `file://`로 직접 열면 등록되지 않으므로 로컬 서버로 띄워야 합니다.
반드시 이 폴더(`tools/fcm-token-tester`)에서 서버를 띄우세요 — 저장소 루트 등 다른
위치에서 띄우면 `index.html`이 없어 엉뚱한 디렉터리 목록이 뜹니다.

```
cd tools/fcm-token-tester
python3 -m http.server 8000
```

Chrome에서 `http://localhost:8000` 접속 → "토큰 발급받기" 클릭 → 알림 권한 허용 →
텍스트박스에 토큰이 표시됩니다.

## 토큰 사용

발급된 토큰을 로그인 API의 `fcmToken`에 넣으면, 그 계정으로 온 알림이 이 브라우저 탭으로
실제 발송됩니다.

```
POST /api/users/login
{
  "loginId": "...",
  "password": "...",
  "fcmToken": "발급받은_토큰",
  "deviceIdentifier": "test-web-1"
}
```

- 탭이 활성 상태면 페이지 안에서 `alert()` 팝업으로 도착 여부를 바로 확인할 수 있습니다.
- 탭이 백그라운드/최소화 상태면 OS 알림 센터로 뜹니다(`firebase-messaging-sw.js`의
  `onBackgroundMessage`가 처리).

## 주의

- 발급된 토큰은 이 브라우저 탭/서비스워커에 묶여 있습니다. 탭을 닫거나 사이트 데이터를
  지우면 무효화될 수 있으니, 다시 필요하면 페이지에서 새로 발급받으면 됩니다.
- 이 도구는 실제 서비스 트래픽과 무관한 개발자 테스트 전용입니다. 프로덕션 사용자에게
  발송하지 않도록, 테스트 계정으로만 로그인해서 사용하세요.
