// 1. 이 파일을 firebase-config.js로 복사하세요 (그 파일은 gitignore되어 있습니다).
// 2. Firebase 콘솔 → 프로젝트 설정(⚙️) → 일반 → "내 앱" → 웹 앱(</>) → SDK 설정 및 구성
//    여기서 나오는 firebaseConfig 객체 값을 그대로 붙여넣으세요.
// 3. 팀 안에서는 이 값들이 전부 동일합니다(프로젝트 단위 식별자라 개인별로 다르지 않음).
//    이미 값을 가진 팀원에게 직접 공유받아도 됩니다 — 다시 콘솔에서 찾을 필요 없습니다.
const firebaseConfig = {
  apiKey: "여기에_apiKey_붙여넣기",
  authDomain: "senioron-8c2e1.firebaseapp.com",
  projectId: "senioron-8c2e1",
  storageBucket: "senioron-8c2e1.firebasestorage.app",
  messagingSenderId: "여기에_messagingSenderId_붙여넣기",
  appId: "여기에_appId_붙여넣기",
};

// Firebase 콘솔 → 프로젝트 설정 → Cloud Messaging → "웹 구성" 탭 → Web Push 인증서
// "키 쌍 생성" 버튼을 누르면 나오는 값을 여기 붙여넣으세요. (이것도 프로젝트 공통값입니다.)
const VAPID_KEY = "여기에_VAPID_키_붙여넣기";
