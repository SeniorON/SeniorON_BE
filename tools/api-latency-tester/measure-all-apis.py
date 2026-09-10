#!/usr/bin/env python3
"""
SeniorON API Latency Benchmark Tool (Python 3 - Zero Dependencies)
Ubuntu 서버 등 Node.js가 설치되어 있지 않은 환경에서도 파이썬 표준 라이브러리만으로 즉시 실행 가능합니다.
"""

import sys
import os
import json
import time
import ssl
import urllib.request
import urllib.error
from datetime import date

# ── CLI 인자 파싱 ──────────────────────────────────────────
args = sys.argv[1:]

def get_arg(key, default_val):
    if key in args:
        idx = args.index(key)
        if idx + 1 < len(args):
            return args[idx + 1]
    return default_val

BASE_URL = get_arg('--url', 'https://senioron.site').rstrip('/')
MODE = get_arg('--mode', 'safe')
ITERATIONS = int(get_arg('--iterations', '3'))
DELAY_SEC = float(get_arg('--delay', '100')) / 1000.0
USERNAME = get_arg('--user', '')
PASSWORD = get_arg('--password', '')
JWT_TOKEN = get_arg('--token', '')
OUTPUT_FILE = get_arg('--output', 'api-latency-report.md')
BENCHMARK_DATE = date.today().isoformat()

# ── ANSI 색상 코드 ──────────────────────────────────────────
C_RESET = '\033[0m'
C_BOLD = '\033[1m'
C_GREEN = '\033[32m'
C_YELLOW = '\033[33m'
C_RED = '\033[31m'
C_CYAN = '\033[36m'

def format_latency(ms):
    if ms < 150:
        return f"{C_GREEN}{ms:.1f}ms{C_RESET}"
    elif ms < 400:
        return f"{C_YELLOW}{ms:.1f}ms{C_RESET}"
    else:
        return f"{C_RED}{C_BOLD}{ms:.1f}ms{C_RESET}"

# SSL 컨텍스트 (기본 검증 활성화)
ssl_ctx = ssl.create_default_context()

# ── API 엔드포인트 정의 ────────────────────────────────────
ENDPOINTS = [
    # 1. 시스템 / 헬스체크
    {
        "domain": "System",
        "name": "Health Check (Readiness)",
        "method": "GET",
        "path": "/actuator/health/readiness",
        "auth": False,
        "safe": True,
    },
    {
        "domain": "System",
        "name": "Swagger API Docs",
        "method": "GET",
        "path": "/v3/api-docs",
        "auth": False,
        "safe": True,
    },

    # 2. User (Auth)
    {
        "domain": "User (Auth)",
        "name": "아이디 중복 확인",
        "method": "GET",
        "path": "/api/users/check-login-id?loginId=benchmark_check_id",
        "auth": False,
        "safe": True,
    },

    # 3. User (Authenticated)
    {
        "domain": "User (Auth)",
        "name": "온보딩 상태 조회",
        "method": "GET",
        "path": "/api/users/me/onboarding-status",
        "auth": True,
        "safe": True,
    },

    # 4. Home (홈 화면)
    {
        "domain": "Home",
        "name": "홈 메인 화면 조회",
        "method": "GET",
        "path": "/api/home",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Home",
        "name": "홈 버튼 옵션 목록 조회",
        "method": "GET",
        "path": "/api/home/button-options",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Home",
        "name": "부모님 홈 화면 조회",
        "method": "GET",
        "path": "/api/home/senior",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Home",
        "name": "오늘의 병원 일정 요약 조회",
        "method": "GET",
        "path": "/api/home/hospitals/today",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Home",
        "name": "단말기 상태 상세 조회",
        "method": "GET",
        "path": "/api/home/device",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Home",
        "name": "현재 날씨 조회",
        "method": "GET",
        "path": "/api/home/weather?latitude=37.5665&longitude=126.978",
        "auth": True,
        "safe": True,
    },

    # 5. Family (가족)
    {
        "domain": "Family",
        "name": "가족 홈 요약 조회",
        "method": "GET",
        "path": "/api/family/home",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Family",
        "name": "가족 구성원 목록 조회",
        "method": "GET",
        "path": "/api/family/members",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Family",
        "name": "가족 초대 코드 조회",
        "method": "GET",
        "path": "/api/family/code",
        "auth": True,
        "safe": True,
    },

    # 6. FamilyPhoto (가족사진)
    {
        "domain": "FamilyPhoto",
        "name": "가족사진 목록 조회",
        "method": "GET",
        "path": "/api/family/photos?page=0&size=10",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "FamilyPhoto",
        "name": "가족 앨범 목록 조회",
        "method": "GET",
        "path": "/api/family/photos/albums",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "FamilyPhoto",
        "name": "가족사진 상세 조회",
        "method": "GET",
        "path": "/api/family/photos/1",
        "auth": True,
        "safe": True,
    },

    # 7. Medication & Logs (복약)
    {
        "domain": "Medication",
        "name": "본인 당일 복약 일정 조회",
        "method": "GET",
        "path": "/api/v1/medications/schedules?date=2026-09-09",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Medication",
        "name": "부모님 복약 그룹 목록 조회",
        "method": "GET",
        "path": "/api/medications/parents/1",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Medication",
        "name": "부모님 당일 복약 스케줄 조회",
        "method": "GET",
        "path": "/api/v1/medications/parents/1/schedules?date=2026-09-09",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Medication",
        "name": "부모님 월간 복약 달력 조회",
        "method": "GET",
        "path": "/api/v1/medications/parents/1/schedules/monthly?year=2026&month=9",
        "auth": True,
        "safe": True,
    },

    # 8. Hospital (병원 일정)
    {
        "domain": "Hospital",
        "name": "부모님 병원 월간 일정 조회",
        "method": "GET",
        "path": "/api/hospitals/parents/1?year=2026&month=9",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Hospital",
        "name": "부모님 특정일 병원 일정 조회",
        "method": "GET",
        "path": "/api/hospitals/parents/1/daily?date=2026-09-09",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Hospital",
        "name": "부모님 다가오는 병원 일정 조회",
        "method": "GET",
        "path": "/api/hospitals/parents/1/upcoming",
        "auth": True,
        "safe": True,
    },

    # 9. Inactivity (안부/미활동 감지)
    {
        "domain": "Inactivity",
        "name": "본인 미활동 설정 조회",
        "method": "GET",
        "path": "/api/inactivity-settings/me",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Inactivity",
        "name": "대상 부모님 미활동 설정 조회",
        "method": "GET",
        "path": "/api/inactivity-settings/1",
        "auth": True,
        "safe": True,
    },

    # 10. Notification (알림)
    {
        "domain": "Notification",
        "name": "알림 목록 조회 (SOS)",
        "method": "GET",
        "path": "/api/notification?type=SOS&size=20",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Notification",
        "name": "알림 수신 설정 조회",
        "method": "GET",
        "path": "/api/notification/setting",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Notification",
        "name": "부모님 단말기 배터리/연결 상태 조회",
        "method": "GET",
        "path": "/api/notification/parent-device-status",
        "auth": True,
        "safe": True,
    },

    # 11. Inquiry (문의사항)
    {
        "domain": "Inquiry",
        "name": "문의사항 목록 조회",
        "method": "GET",
        "path": "/api/inquiries?page=0&size=10",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Inquiry",
        "name": "문의사항 상세 조회",
        "method": "GET",
        "path": "/api/inquiries/1",
        "auth": True,
        "safe": True,
    },

    # 12. Device (기기 관리)
    {
        "domain": "Device",
        "name": "단말기 최근 위치 조회",
        "method": "GET",
        "path": "/api/devices/location",
        "auth": True,
        "safe": True,
    },
    {
        "domain": "Device",
        "name": "홈(집) 등록 위치 조회",
        "method": "GET",
        "path": "/api/devices/home-location",
        "auth": True,
        "safe": True,
    },

    # 13. Event (이벤트 조회)
    {
        "domain": "Event",
        "name": "이벤트 상세 조회",
        "method": "GET",
        "path": "/api/event/1",
        "auth": True,
        "safe": True,
    },

    # ── PUT / PATCH / POST (--mode all 에서만 실행) ──────────────────

    # Home 변경
    {
        "domain": "Home",
        "name": "글자 크기 설정 변경 (PATCH)",
        "method": "PATCH",
        "path": "/api/home/font-size",
        "auth": True,
        "safe": False,
        "setup": "home_font_size",
    },
    {
        "domain": "Home",
        "name": "홈 버튼 배치 저장 (PUT)",
        "method": "PUT",
        "path": "/api/home/buttons",
        "auth": True,
        "safe": False,
        "setup": "home_buttons",
    },

    # Notification 설정 토글
    {
        "domain": "Notification",
        "name": "알림 수신 설정 토글 (PATCH)",
        "method": "PATCH",
        "path": "/api/notification/setting/INACTIVITY",
        "auth": True,
        "safe": False,
        "body": {"enabled": True},
    },
    {
        "domain": "Notification",
        "name": "알림 읽음 처리 (PATCH)",
        "method": "PATCH",
        "path": "/api/notification/1/read",
        "auth": True,
        "safe": False,
    },

    # Inactivity 설정 변경
    {
        "domain": "Inactivity",
        "name": "미활동 감지 임계시간 변경 (PATCH)",
        "method": "PATCH",
        "path": "/api/inactivity-settings/1",
        "auth": True,
        "safe": False,
        "body": {"thresholdHours": 8},
    },

    # Family 관리
    {
        "domain": "Family",
        "name": "가족 대표 관리자 변경 (PATCH)",
        "method": "PATCH",
        "path": "/api/family/primary-manager",
        "auth": True,
        "safe": False,
        "body": {"targetUserId": 1},
    },

    # Medication Log 복약 체크
    {
        "domain": "Medication",
        "name": "가장 가까운 복약 일정 체크 (PATCH)",
        "method": "PATCH",
        "path": "/api/v1/medication-logs/check",
        "auth": True,
        "safe": False,
    },
    {
        "domain": "Medication",
        "name": "특정 복약 일정 체크 (PATCH)",
        "method": "PATCH",
        "path": "/api/v1/medication-logs/1/check",
        "auth": True,
        "safe": False,
    },

    # Device 상태/FCM 업데이트
    {
        "domain": "Device",
        "name": "단말기 배터리 상태 업데이트 (PUT)",
        "method": "PUT",
        "path": "/api/devices/status",
        "auth": True,
        "safe": False,
        "body": {"deviceIdentifier": "bench-device-001", "deviceName": "Galaxy Watch", "batteryLevel": 80},
    },
    {
        "domain": "Device",
        "name": "단말기 FCM 토큰 갱신 (PATCH)",
        "method": "PATCH",
        "path": "/api/devices/fcm-token",
        "auth": True,
        "safe": False,
        "body": {"deviceIdentifier": "bench-device-001", "deviceToken": "bench-dummy-fcm-token-12345"},
    },
    {
        "domain": "Device",
        "name": "단말기 위치 업데이트 (PATCH)",
        "method": "PATCH",
        "path": "/api/devices/location",
        "auth": True,
        "safe": False,
        "body": {"deviceIdentifier": "bench-device-001", "latitude": 37.5665, "longitude": 126.978},
    },

    # Event (SOS, 외출/귀가)
    {
        "domain": "Event",
        "name": "SOS 긴급 알림 발생 (POST)",
        "method": "POST",
        "path": "/api/event/sos",
        "auth": True,
        "safe": False,
        "body": {"latitude": 37.5665, "longitude": 126.978},
    },
    {
        "domain": "Event",
        "name": "외출/귀가 이벤트 전송 (POST)",
        "method": "POST",
        "path": "/api/event/outing-return",
        "auth": True,
        "safe": False,
        "body": {"eventType": "RETURN"},
    },

    # Family 관리 (초대/참여)
    {
        "domain": "Family",
        "name": "가족 초대 코드 생성 (POST)",
        "method": "POST",
        "path": "/api/family/code-create",
        "auth": True,
        "safe": False,
    },
    {
        "domain": "Family",
        "name": "가족 참여 (POST)",
        "method": "POST",
        "path": "/api/family/join",
        "auth": True,
        "safe": False,
        "body": {"code": "BENCH01"},
    },

    # FamilyPhoto 관리
    {
        "domain": "FamilyPhoto",
        "name": "가족사진 업로드 URL 발급 (POST)",
        "method": "POST",
        "path": "/api/family/photos/upload-url",
        "auth": True,
        "safe": False,
        "body": {"fileName": "bench-test.jpg", "contentType": "image/jpeg"},
    },
    {
        "domain": "FamilyPhoto",
        "name": "가족사진 업로드 완료 처리 (POST)",
        "method": "POST",
        "path": "/api/family/photos/complete",
        "auth": True,
        "safe": False,
        "body": {"photoKey": "bench-test-key"},
    },
    {
        "domain": "FamilyPhoto",
        "name": "가족사진 열람 처리 (PATCH)",
        "method": "PATCH",
        "path": "/api/family/photos/1/viewed",
        "auth": True,
        "safe": False,
    },

    # 회원가입 / 로그인 / 토큰 (User)
    {
        "domain": "User (Auth)",
        "name": "회원가입 이메일 인증코드 발송 (POST)",
        "method": "POST",
        "path": "/api/users/signup/email/verification-code",
        "auth": False,
        "safe": False,
        "body": {"email": "bench_dummy@test.com"},
    },
    {
        "domain": "User (Auth)",
        "name": "회원가입 이메일 인증코드 확인 (POST)",
        "method": "POST",
        "path": "/api/users/signup/email/verification-code/verify",
        "auth": False,
        "safe": False,
        "body": {"email": "bench_dummy@test.com", "code": "123456"},
    },
    {
        "domain": "User (Auth)",
        "name": "회원가입 (POST, 매 실행마다 계정 생성됨)",
        "method": "POST",
        "path": "/api/users/signup",
        "auth": False,
        "safe": False,
        "body": {
            "loginId": "bench_dummy_user", "password": "BenchPass123!",
            "name": "벤치테스트", "email": "bench_dummy@test.com", "phoneNumber": "010-0000-0000"
        },
    },
    {
        "domain": "User (Auth)",
        "name": "로그인 (POST)",
        "method": "POST",
        "path": "/api/users/login",
        "auth": False,
        "safe": False,
        "body": {"loginId": USERNAME, "password": PASSWORD},
    },
    {
        "domain": "User (Auth)",
        "name": "토큰 재발급 (POST)",
        "method": "POST",
        "path": "/api/users/token/refresh",
        "auth": False,
        "safe": False,
        "body": {"refreshToken": "CHANGE_ME"},
    },
    {
        "domain": "User (Auth)",
        "name": "로그아웃 (POST)",
        "method": "POST",
        "path": "/api/users/logout",
        "auth": True,
        "safe": False,
    },

    # Hospital 일정 등록
    {
        "domain": "Hospital",
        "name": "부모님 병원 일정 등록 (POST)",
        "method": "POST",
        "path": "/api/hospitals/parents/{parentUserId}",
        "auth": True,
        "safe": False,
        "setup": "parent_hospital",
    },

    # Medication 그룹 관리
    {
        "domain": "Medication",
        "name": "부모님 복약 그룹 등록 (POST)",
        "method": "POST",
        "path": "/api/medications/parents/{parentUserId}",
        "auth": True,
        "safe": False,
        "setup": "parent_medication",
    },
    {
        "domain": "Medication",
        "name": "부모님 복약 그룹 수정 (PUT)",
        "method": "PUT",
        "path": "/api/medications/parents/1",
        "auth": True,
        "safe": False,
        "body": {"medicationName": "혈압약", "dosage": "1정", "times": ["08:00", "20:00"]},
    },

    # Event 추가 이벤트
    {
        "domain": "Event",
        "name": "미활동 이벤트 전송 (POST)",
        "method": "POST",
        "path": "/api/event/inactivity",
        "auth": True,
        "safe": False,
    },
    {
        "domain": "Event",
        "name": "위험 감지 링크 이벤트 전송 (POST)",
        "method": "POST",
        "path": "/api/event/risk-link",
        "auth": True,
        "safe": False,
    },
]

BENCHMARK_CONTEXT = {}

def fetch_json(path, token):
    headers = {"Accept": "application/json", "User-Agent": "SeniorON-Bench/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(f"{BASE_URL}{path}", headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=10, context=ssl_ctx) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, None
    except (urllib.error.URLError, json.JSONDecodeError):
        return 0, None

def fallback_home_buttons():
    required_buttons = ["MEDICATION", "COMPANION", "PHOTO", "EMERGENCY"]
    buttons = [
        {
            "buttonOrder": index,
            "buttonName": button_name,
            "actionType": "DEFAULT",
            "actionValue": button_name,
            "packageName": None,
        }
        for index, button_name in enumerate(required_buttons, start=1)
    ]
    buttons.extend(
        {
            "buttonOrder": index,
            "buttonName": f"성능측정 앱 {index}",
            "actionType": "APP",
            "actionValue": None,
            "packageName": f"com.senioron.benchmark.app{index}",
        }
        for index in range(5, 9)
    )
    return buttons

def build_benchmark_context(token):
    context = {}
    member_status, member_response = fetch_json("/api/family/members", token)
    if member_status == 200 and member_response:
        members = member_response.get("data", [])
        parent = next((member for member in members if member.get("role") == "PARENT"), None)
        if parent and parent.get("usersId") is not None:
            context["parent_user_id"] = parent["usersId"]
        else:
            print("⚠️ 같은 가족의 PARENT 사용자를 찾지 못했습니다.")
    else:
        print(f"⚠️ 가족 구성원 사전 조회 실패 (HTTP {member_status})")

    home_status, home_response = fetch_json("/api/home", token)
    if home_status == 200 and home_response:
        home = home_response.get("data", {})
        context["font_size"] = home.get("font_size", "MEDIUM")

        current_buttons = home.get("buttons")
        if isinstance(current_buttons, list) and current_buttons:
            context["buttons"] = [
                {
                    "buttonOrder": button.get("button_order"),
                    "buttonName": button.get("button_name"),
                    "actionType": button.get("action_type"),
                    "actionValue": button.get("action_value"),
                    "packageName": button.get("package_name"),
                }
                for button in current_buttons
            ]
        else:
            context["buttons"] = fallback_home_buttons()

        music_card = home.get("music_card") or {}
        context["music_app"] = music_card.get("music_app")
    else:
        print(f"⚠️ 홈 설정 사전 조회 실패 (HTTP {home_status})")

    return context

def prepare_endpoint(endpoint, token):
    prepared = endpoint.copy()
    setup = endpoint.get("setup")

    if setup == "home_font_size":
        prepared["body"] = {"font_size": BENCHMARK_CONTEXT.get("font_size", "MEDIUM")}
    elif setup == "home_buttons":
        prepared["body"] = {
            "musicApp": BENCHMARK_CONTEXT.get("music_app"),
            "buttons": BENCHMARK_CONTEXT.get("buttons", fallback_home_buttons()),
        }
    elif setup in {"parent_hospital", "parent_medication"}:
        parent_user_id = BENCHMARK_CONTEXT.get("parent_user_id")
        if parent_user_id is not None:
            prepared["path"] = prepared["path"].format(parentUserId=parent_user_id)
        if setup == "parent_hospital":
            prepared["body"] = {
                "hospitalName": "성능측정 병원",
                "department": "내과",
                "scheduleDate": BENCHMARK_DATE,
                "scheduleTime": "10:00",
                "reminderType": "NONE",
            }
        else:
            prepared["body"] = {
                "medicineName": "성능측정용 약",
                "ingredientName": "벤치마크 성분",
                "medicineTimes": ["08:00"],
                "startDate": BENCHMARK_DATE,
                "repeatType": "DAILY",
                "repeatInterval": 1,
                "medicineDays": [],
                "repeatEndType": "DURATION",
                "durationWeeks": 1,
                "endDate": None,
            }

    return prepared

def measure_request(ep, token):
    ep = prepare_endpoint(ep, token)
    url = f"{BASE_URL}{ep['path']}"
    headers = {"Accept": "application/json", "User-Agent": "SeniorON-Bench/1.0"}

    data = None
    if "body" in ep and ep["body"] is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(ep["body"]).encode("utf-8")

    if ep["auth"] and token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, headers=headers, method=ep["method"])

    start = time.perf_counter()
    status = 0
    error_msg = None

    try:
        with urllib.request.urlopen(req, timeout=10, context=ssl_ctx) as resp:
            status = resp.status
            resp.read()
    except urllib.error.HTTPError as e:
        status = e.code
    except urllib.error.URLError as e:
        error_msg = str(e.reason)
    except Exception as e:
        error_msg = str(e)

    duration_ms = (time.perf_counter() - start) * 1000.0
    return {
        "duration": duration_ms,
        "status": status,
        "error": error_msg,
        "path": ep["path"],
    }

def login_and_get_token(username, password):
    url = f"{BASE_URL}/api/users/login"
    print(f"🔐 로그인 시도 중... [{username}] -> {url}")
    payload = json.dumps({"loginId": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=5, context=ssl_ctx) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("data", {}).get("accessToken") or data.get("accessToken")
            if token:
                print(f"✅ 로그인 성공! JWT AccessToken 획득 완료\n")
                return token
            else:
                print(f"⚠️ accessToken을 찾지 못했습니다: {data}")
                return None
    except urllib.error.HTTPError as e:
        print(f"❌ 로그인 실패 (HTTP {e.code}): 계정 정보를 확인해주세요.")
        return None
    except Exception as e:
        print(f"❌ 로그인 요청 에러: {e}")
        return None

def main():
    global JWT_TOKEN, BENCHMARK_CONTEXT

    print(f"\n================================================================")
    print(f"  🚀 SeniorON API Latency Benchmark Runner (Python 3)")
    print(f"================================================================")
    print(f"  대상 서버   : {C_CYAN}{BASE_URL}{C_RESET}")
    print(f"  측정 모드   : {C_BOLD}{'전체 (CUD 포함)' if MODE == 'all' else '안전 모드 (조회 위주)'}{C_RESET}")
    print(f"  반복 횟수   : {ITERATIONS}회 측정 (평균/최소/최대/P95 산출)")
    print(f"  요청 간 지연: {int(DELAY_SEC * 1000)}ms")
    print(f"================================================================\n")

    if not JWT_TOKEN and USERNAME and PASSWORD:
        JWT_TOKEN = login_and_get_token(USERNAME, PASSWORD)
    elif JWT_TOKEN:
        print(f"🔑 전달받은 JWT 토큰을 사용하여 요청합니다.\n")
    else:
        print(f"⚠️  인증 정보가 지정되지 않았습니다. (미인증 시 HTTP 401 반환 가능)\n")

    if MODE == 'all' and JWT_TOKEN:
        print("🔎 CUD 요청의 계정/가족/홈 설정을 확인하는 중...")
        BENCHMARK_CONTEXT = build_benchmark_context(JWT_TOKEN)
        parent_user_id = BENCHMARK_CONTEXT.get("parent_user_id")
        if parent_user_id is not None:
            print(f"✅ 현재 가족의 부모 사용자 ID를 {parent_user_id}로 확인했습니다.\n")
        else:
            print("⚠️ 부모 사용자 ID를 확인하지 못해 병원/복약 등록은 실패할 수 있습니다.\n")

    target_endpoints = [ep for ep in ENDPOINTS if (True if MODE == 'all' else ep['safe'])]
    print(f"📋 총 {len(target_endpoints)}개 엔드포인트 측정을 시작합니다...\n")

    results = []

    for i, ep in enumerate(target_endpoints):
        progress = f"[{i+1}/{len(target_endpoints)}]"
        path_display = ep['path'][:38]
        sys.stdout.write(f"  {progress} {ep['method']:<6} {path_display:<40} ... ")
        sys.stdout.flush()

        samples = []
        last_status = 0
        last_error = None
        last_path = ep["path"]

        for _ in range(ITERATIONS):
            m = measure_request(ep, JWT_TOKEN)
            samples.append(m["duration"])
            last_status = m["status"]
            last_path = m["path"]
            if m["error"]:
                last_error = m["error"]
            if DELAY_SEC > 0:
                time.sleep(DELAY_SEC)

        samples.sort()
        min_v = samples[0]
        max_v = samples[-1]
        avg_v = sum(samples) / len(samples)
        p95_v = samples[int(len(samples) * 0.95)]

        if 200 <= last_status < 300:
            status_color = C_GREEN
        elif last_status == 401:
            status_color = C_YELLOW
        else:
            status_color = C_RED

        status_str = f"{C_RED}{last_error}{C_RESET}" if last_error else f"{status_color}{last_status}{C_RESET}"

        print(f"status: {status_str} | avg: {format_latency(avg_v)} (min: {min_v:.0f}ms, max: {max_v:.0f}ms)")

        results.append({
            "domain": ep["domain"],
            "name": ep["name"],
            "method": ep["method"],
            "path": last_path,
            "status": last_status,
            "error": last_error,
            "min": min_v,
            "avg": avg_v,
            "max": max_v,
            "p95": p95_v,
        })

    # 느린 순 정렬
    results.sort(key=lambda r: r["avg"], reverse=True)

    print(f"\n================================================================")
    print(f"  📊 측정 결과 요약 (가장 느린 API 순서)")
    print(f"================================================================")
    print(f"  {'METHOD':<7} {'PATH':<40} {'STATUS':<8} {'AVG':>8} {'P95':>8}  도메인/이름")
    print(f"  ------------------------------------------------------------------------------------------------")

    for r in results:
        path_t = r["path"][:35] + "..." if len(r["path"]) > 38 else r["path"]
        status_disp = (r["error"] if r["error"] else str(r["status"]))[:8]
        print(f"  {r['method']:<7} {path_t:<40} {status_disp:<8} {format_latency(r['avg']):>16} {format_latency(r['p95']):>16}  [{r['domain']}] {r['name']}")

    total_apis = len(results)
    avg_total = sum(r["avg"] for r in results) / total_apis if total_apis else 0
    fast_apis = len([r for r in results if r["avg"] < 150])
    normal_apis = len([r for r in results if 150 <= r["avg"] < 400])
    slow_apis = len([r for r in results if r["avg"] >= 400])

    print(f"\n----------------------------------------------------------------")
    print(f"  총 측정 API  : {total_apis}개")
    print(f"  전체 평균 속도: {avg_total:.1f}ms")
    print(f"  분포          : 🟢 빠름(<150ms): {fast_apis}개 | 🟡 보통(150~400ms): {normal_apis}개 | 🔴 병목의심(>400ms): {slow_apis}개")
    print(f"================================================================\n")

    # 마크다운 리포트 저장
    save_markdown(results, avg_total, total_apis, fast_apis, normal_apis, slow_apis)

def save_markdown(results, avg_total, total_apis, fast_apis, normal_apis, slow_apis):
    top_slow = results[:5]
    date_str = time.strftime("%Y-%m-%d %H:%M:%S")

    md = f"# SeniorON API 응답 속도 측정 리포트\n\n"
    md += f"- **측정 일시**: {date_str}\n"
    md += f"- **대상 서버**: `{BASE_URL}`\n"
    md += f"- **측정 모드**: `{MODE}` (반복: {ITERATIONS}회)\n"
    md += f"- **전체 API 평균 속도**: **{avg_total:.1f}ms**\n"
    md += f"- **속도 분포**: 🟢 빠름(<150ms): **{fast_apis}개** | 🟡 보통(150~400ms): **{normal_apis}개** | 🔴 병목(>400ms): **{slow_apis}개**\n\n"

    md += f"## 🚨 가장 느린 API Top 5 (최적화 우선 대상)\n\n"
    md += f"| 순위 | 도메인 | 메서드 | 경로 | 평균(Avg) | P95 | 상태코드 | 비고 |\n"
    md += f"| :---: | :--- | :---: | :--- | :---: | :---: | :---: | :--- |\n"
    for idx, r in enumerate(top_slow):
        md += f"| {idx + 1} | {r['domain']} | `{r['method']}` | `{r['path']}` | **{r['avg']:.1f}ms** | {r['p95']:.1f}ms | `{r['status']}` | {r['name']} |\n"

    md += f"\n## 📋 전체 API 응답 속도 상세 결과\n\n"
    md += f"| 도메인 | API 설명 | 메서드 | 엔드포인트 | 상태코드 | 평균(Avg) | 최소(Min) | 최대(Max) | P95 |\n"
    md += f"| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: |\n"

    for r in results:
        badge = '🟢' if r['avg'] < 150 else ('🟡' if r['avg'] < 400 else '🔴')
        md += f"| {r['domain']} | {r['name']} | `{r['method']}` | `{r['path']}` | `{r['status']}` | {badge} **{r['avg']:.1f}ms** | {r['min']:.0f}ms | {r['max']:.0f}ms | {r['p95']:.1f}ms |\n"

    md += f"\n---\n*Generated automatically by SeniorON API Latency Benchmark Tool (Python)*\n"

    try:
        with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
            f.write(md)
        print(f"📄 마크다운 리포트가 저장되었습니다: {OUTPUT_FILE}")
    except Exception as e:
        print(f"❌ 리포트 파일 저장 오류: {e}")

if __name__ == '__main__':
    main()