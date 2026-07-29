// FCM 웹 푸시는 서비스워커가 반드시 있어야 토큰 발급이 됩니다.
// 파일 이름(firebase-messaging-sw.js)과 위치(사이트 루트)를 바꾸면 안 됩니다.

importScripts("https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js");
importScripts("./firebase-config.js");

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

// register()는 설치를 시작만 시킬 뿐, 활성화까지 기다려주지 않는다.
// skipWaiting/clients.claim으로 재로드 없이 즉시 활성화 + 현재 탭 제어를 강제한다.
// 이게 없으면 getToken()이 PushManager.subscribe()를 호출할 때 "no active Service Worker" 에러가 난다.
self.addEventListener("install", (event) => {
  self.skipWaiting();
});
self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

// 브라우저 탭이 백그라운드/닫힌 상태일 때 푸시가 오면 여기서 처리된다.
// onBackgroundMessage를 등록하면 Firebase의 기본 알림 표시가 꺼지므로, 직접 showNotification을 호출해야
// 실제로 화면(OS 알림)에 뜬다. 콘솔 로그만 남기면 서비스워커 전용 콘솔에만 찍히고 눈에는 안 보인다.
messaging.onBackgroundMessage((payload) => {
  console.log("[firebase-messaging-sw.js] 백그라운드 메시지 수신:", payload);

  const title = payload.notification?.title || "SeniorON";
  const options = {
    body: payload.notification?.body || "",
    icon: "/favicon.ico",
  };
  self.registration.showNotification(title, options);
});
