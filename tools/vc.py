#!/usr/bin/env python3
"""Pleos Virtual Console 시연·검증 도구.

가상 콘솔은 브라우저 페이지가 1초마다 보내는 하트비트(/browser/session)로 "켜짐"을 판단한다.
브라우저 탭이 뒤로 가면 하트비트가 늦어져 몇 초 만에 503(emulator_inactive)이 되고, 그동안 보낸 명령은
조용히 무시된다. 이 도구는 하트비트를 직접 보내고, 모든 명령을 보낸 뒤 상태를 다시 읽어 실제로 바뀌었는지 확인한다.

사용법 (저장소 루트에서, 에뮬레이터 실행 중):
  python3 tools/vc.py status                 콘솔·앱 상태 확인
  python3 tools/vc.py keepalive              시연 내내 콘솔을 켜 둔다(Ctrl+C로 종료)
  python3 tools/vc.py scenario all           주행 시나리오를 실행하고 앱 기록을 자동 검증
  python3 tools/vc.py scenario commute|cabin|rain|parking|harsh
  python3 tools/vc.py demo suggest           시연용 12주 기록 넣기(steady|watch|suggest|improve|commuter|clear)
  python3 tools/vc.py set gear Drive         값 하나 바꾸고 적용 확인(gear|wiper|odometer)
  python3 tools/vc.py set windows 100        창문 네 곳(0 닫힘 ~ 100 끝까지 내림), temp 17(공조 온도 16~30),
                                             fan 8(풍량 1~8), seatheat 3(운전석 열선 1~3, 통풍 -1~-3, 0 끔),
                                             trunk true, wiper Off|Auto|Low|High
  python3 tools/vc.py measure 60             주행 중 부하 측정: 앱을 뒤로 두고 신호를 계속 바꾸며 CPU·메모리 기록
                                             (Pleos 규정: 주행 중 비내비 앱 CPU 5% 미만, RAM 400MB 이하)

표준 라이브러리만 쓴다. 실패하면 종료 코드 1.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Callable

CONSOLE_PORT = 8088
BASE = f"http://127.0.0.1:{CONSOLE_PORT}"
PACKAGE = "kr.chatoon.app"
DRIVER_USER = "10"  # Pleos Connect 운전자 사용자
HEARTBEAT_CLIENT = "chatoon-vc-tool"
VERIFY_TIMEOUT_S = 4.0
ODOMETER_TOLERANCE_KM = 0.01
SETTLE_S = 6.0  # 주행거리 신호 지연(에뮬레이터에서 약 2초, 4초 주기 반복)보다 넉넉하게

# 간섭 검사 속도: 덮어쓰기를 알아볼 만큼 0이 아니고, 0으로 되돌려도 급감속 기준(7.5km/h/s) 아래인 값.
INTERFERENCE_PROBE_KPH = 5


class ToolError(Exception):
    """사용자에게 그대로 보여 줄 실패."""


# 가상 콘솔이 창문 네 곳을 받는 이름(/vehicle/body). 상태에서는 front_left·front_right·rear_left·rear_right로 읽힌다.
WINDOW_FIELDS = ("window_fl", "window_fr", "window_rl", "window_rr")


def _require_range(what: str, value: float, low: float, high: float) -> None:
    if not low <= value <= high:
        raise ToolError(f"{what} 값은 {low}~{high} 사이여야 합니다(받은 값 {value}).")


def _truthy(text: str) -> bool:
    return text.lower() in ("1", "true", "yes", "on")


# ---------------------------------------------------------------- adb

def adb_path() -> str:
    found = shutil.which("adb")
    if found:
        return found
    default = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
    if os.path.exists(default):
        return default
    raise ToolError("adb를 찾을 수 없습니다. Android SDK platform-tools를 설치하거나 PATH에 넣어 주세요.")


def adb(*args: str, check: bool = True) -> str:
    result = subprocess.run([adb_path(), *args], capture_output=True, text=True, timeout=30)
    if check and result.returncode != 0:
        raise ToolError(f"adb {' '.join(args)} 실패: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def ensure_device() -> None:
    lines = [l for l in adb("devices").splitlines()[1:] if l.strip()]
    if not any(l.endswith("\tdevice") for l in lines):
        raise ToolError("연결된 에뮬레이터가 없습니다. Pleos 에뮬레이터를 먼저 실행해 주세요.")
    adb("forward", f"tcp:{CONSOLE_PORT}", f"tcp:{CONSOLE_PORT}")


# 앱이 암호화해 저장하는 파일. debug 빌드의 PlainExportReceiver로 풀어서 읽는다.
ENCRYPTED_FILES = {"drive-log.tsv", "drive-log.tsv.checkpoint", "profile.txt"}


def app_file(name: str) -> str | None:
    """앱 전용 폴더의 파일 내용(암호화된 파일은 푼 내용). 없으면 None."""
    path = f"files/{name}"
    if name in ENCRYPTED_FILES:
        result = adb("shell", "am", "broadcast", "--user", DRIVER_USER, "-a", f"{PACKAGE}.EXPORT_PLAIN",
                     "-n", f"{PACKAGE}/.debug.PlainExportReceiver", "--es", "name", name)
        code = re.search(r"result=(-?\d+)", result)
        if code is None:
            raise ToolError(f"기록을 풀어 내보내지 못했습니다(debug 빌드인지 확인해 주세요): {result.strip()}")
        if code.group(1) == "1":
            return None
        if code.group(1) != "0":
            raise ToolError(f"기록을 풀어 내보내지 못했습니다(결과 {code.group(1)}). logcat의 Chatoon/Export를 확인해 주세요.")
        path = f"cache/plain/{name}"
    out = subprocess.run(
        [adb_path(), "shell", "run-as", PACKAGE, "--user", DRIVER_USER, "cat", path],
        capture_output=True, text=True, timeout=30,
    )
    if name in ENCRYPTED_FILES:  # 푼 사본은 바로 지운다
        subprocess.run([adb_path(), "shell", "run-as", PACKAGE, "--user", DRIVER_USER, "rm", "-f", path],
                       capture_output=True, timeout=30)
    return out.stdout if out.returncode == 0 else None


def require_no_console_interference(console: "Console") -> None:
    """가상 콘솔 웹 페이지가 화면에 떠 있으면 그 페이지의 관성 시뮬레이션이 속도를 계속 덮어써
    급가속·급감속 시나리오를 망친다(숨긴 탭은 시뮬레이션이 멈춰 괜찮다). 열린 페이지 수가 아니라 실제로 덮어써지는지 시험한다.
    P단에서 시험하므로 앱에 주행 기록이 생기지 않는다.
    시험 속도는 낮게 둔다: 예전에 30으로 시험하자 30→0이 급감속(초당 7.5km/h 이상)으로 세어지고, 그 시점이 바로 다음
    주행의 출발과 겹치면 그 주행에 급감속 1번이 더 붙었다(2026-11-03 실험, research/experiments/2026-11-03-release-real-drive).
    5→0은 기준 아래라 세지 않는다."""
    console.gear("Park")
    console.speed(INTERFERENCE_PROBE_KPH)
    readings = []
    for _ in range(4):
        time.sleep(0.4)
        readings.append(int(console.state()["controls"]["speed_kph"]))
    console.speed(0)
    if any(r != INTERFERENCE_PROBE_KPH for r in readings):
        raise ToolError(
            f"차량 속도가 저절로 바뀝니다({readings}). 화면에 떠 있는 가상 콘솔 웹 페이지의 관성 시뮬레이션이 덮어쓰는 것입니다. "
            "브라우저의 콘솔 탭을 닫거나 다른 탭으로 옮긴 뒤 다시 실행해 주세요."
        )


def speed_permission_granted() -> bool:
    """운전자 사용자에게 차량 속도 권한(급가속·급감속)이 허용돼 있는가."""
    dump = adb("shell", "dumpsys", "package", PACKAGE)
    section = dump.split(f"User {DRIVER_USER}:", 1)[-1].split("User ", 1)[0]
    return "android.car.permission.CAR_SPEED: granted=true" in section


def trip_lines() -> list[str]:
    content = app_file("drive-log.tsv") or ""
    return [l for l in content.splitlines() if l.startswith("C1\t")]


# ---------------------------------------------------------------- 콘솔

class Console:
    """하트비트를 유지하며 명령을 보내고, 상태를 다시 읽어 적용을 확인한다."""

    def __init__(self) -> None:
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._heartbeat_loop, name="vc-heartbeat", daemon=True)

    def __enter__(self) -> "Console":
        self._send_heartbeat()
        self._thread.start()
        self._wait_active()
        return self

    def __exit__(self, *_: object) -> None:
        self._stop.set()
        self._thread.join(timeout=2)

    # 하트비트 -----------------------------------------------------

    def _send_heartbeat(self) -> None:
        try:
            self._request("POST", f"/browser/session?client={HEARTBEAT_CLIENT}")
        except ToolError:
            pass  # 일시 실패는 다음 주기에 다시 보낸다

    def _heartbeat_loop(self) -> None:
        while not self._stop.wait(1.0):
            self._send_heartbeat()

    def _wait_active(self, timeout_s: float = 8.0) -> None:
        deadline = time.monotonic() + timeout_s
        while time.monotonic() < deadline:
            if self.state().get("odometer", {}).get("emulator_active"):
                return
            time.sleep(0.5)
        raise ToolError("가상 콘솔이 켜지지 않습니다. 에뮬레이터가 실행 중인지 확인해 주세요.")

    # HTTP -----------------------------------------------------------

    @staticmethod
    def _request(method: str, path: str) -> dict:
        req = urllib.request.Request(BASE + path, method=method, data=b"" if method == "POST" else None)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                body = resp.read().decode() or "{}"
                return json.loads(body) if body.strip().startswith("{") else {}
        except urllib.error.HTTPError as e:
            raise ToolError(f"{method} {path} → HTTP {e.code} {e.read().decode()[:120]}") from e
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            raise ToolError(f"가상 콘솔에 연결할 수 없습니다({BASE}): {e}") from e

    def state(self) -> dict:
        return self._request("GET", "/vehicle/state")

    def _post(self, path: str, **params: object) -> None:
        query = urllib.parse.urlencode({k: str(v).lower() if isinstance(v, bool) else v for k, v in params.items()})
        for attempt in range(3):
            try:
                self._request("POST", f"{path}?{query}")
                return
            except ToolError as e:
                if "503" not in str(e) or attempt == 2:
                    raise
                self._send_heartbeat()
                time.sleep(1.0)

    def _verify(
        self,
        what: str,
        read: Callable[[dict], object],
        expected: object,
        matches: Callable[[object, object], bool] = lambda a, e: a == e,
    ) -> None:
        deadline = time.monotonic() + VERIFY_TIMEOUT_S
        actual: object = None
        while time.monotonic() < deadline:
            actual = read(self.state())
            if matches(actual, expected):
                return
            time.sleep(0.2)
        raise ToolError(f"{what}: 명령을 보냈지만 적용되지 않았습니다(기대 {expected!r}, 실제 {actual!r}).")

    # 명령(보낸 뒤 반드시 확인) -------------------------------------

    def gear(self, gear: str) -> None:
        self._post("/vehicle/gear", gear=gear)
        self._verify(f"기어 {gear}", lambda s: ((s.get("gear") or {}).get("actual") or {}).get("value"), gear)

    def wiper(self, mode: str) -> None:
        self._post("/vehicle/body", front_wiper=mode)
        self._verify(f"와이퍼 {mode}", lambda s: s["body"]["wipers"]["front"]["mode"], mode)

    # 아래 다섯은 차툰(차가 그리는 4컷 만화)의 소재가 되는 신호다. 명령 이름과 상태 경로는 콘솔의 명령 목록
    # (GET /vehicle/catalog)과 2026-11-12 탐침 기록(research/experiments/2026-11-12-cartoon-probe)에서 왔다.

    def windows(self, percent: int) -> None:
        """창문 네 곳을 한꺼번에 움직인다. 0이 닫힘, 100이 끝까지 내림이다(안드로이드 차량 표준 WINDOW_POS의 뜻이고
        콘솔 기본값이 0이다. Pleos 문서에는 방향이 적혀 있지 않다). 앱은 같은 값을 0~10으로 읽는다(100 → 10)."""
        _require_range("창문", percent, 0, 100)
        self._post("/vehicle/body", **{field: percent for field in WINDOW_FIELDS})
        self._verify(f"창문 {percent}", lambda s: sorted(set(s["body"]["windows"].values())), [percent])

    def cabin_temp(self, celsius: float) -> None:
        """운전석 공조 설정 온도(16~30도, 0.5도 단위)."""
        _require_range("공조 온도", celsius, 16, 30)
        if (celsius * 2) != int(celsius * 2):
            raise ToolError(f"공조 온도는 0.5도 단위여야 합니다(받은 값 {celsius}).")
        self._post("/vehicle/hvac", driver_temp=int(celsius) if float(celsius).is_integer() else celsius)
        self._verify(f"공조 온도 {celsius}", lambda s: float(s["hvac"]["driver"]["temp_c"]), float(celsius))

    def fan(self, level: int) -> None:
        """공조 풍량(1~8)."""
        _require_range("풍량", level, 1, 8)
        self._post("/vehicle/hvac", fan_speed=level)
        self._verify(f"풍량 {level}", lambda s: int(s["hvac"]["fan_speed"]), level)

    def seat_heat(self, level: int) -> None:
        """운전석 시트 열선(1~3)과 통풍(-1~-3). 0이면 끈다."""
        _require_range("시트 열선·통풍", level, -3, 3)
        self._post("/vehicle/hvac", driver_seat=level)
        self._verify(f"시트 열선·통풍 {level}", lambda s: int(s["hvac"]["driver"]["seat_temp"]), level)

    def ambient(self, color: str, brightness_pct: int) -> None:
        """실내 조명(앰비언트 라이트) 색(#rrggbb)과 밝기(0~100)를 정하고 켠다. 앱의 조명 연출 전 기준값을 맞출 때 쓴다."""
        if not re.fullmatch(r"#[0-9a-fA-F]{6}", color):
            raise ToolError(f"조명 색은 #rrggbb 형식이어야 합니다(받은 값 {color}).")
        _require_range("조명 밝기", brightness_pct, 0, 100)
        self._post("/vehicle/body", ambient_mode="On", ambient_color=color.lower(), ambient_brightness=brightness_pct)
        self._verify(f"실내 조명 {color} {brightness_pct}", lambda s: (s["body"]["ambient_light"]["color"].lower(),
                     int(s["body"]["ambient_light"]["brightness_pct"])), (color.lower(), brightness_pct))

    def trunk(self, opened: bool) -> None:
        self._post("/vehicle/body", trunk=opened)
        self._verify(f"트렁크 {'열림' if opened else '닫힘'}", lambda s: s["body"]["closures"]["trunk"], opened)

    def odometer(self, km: float) -> None:
        self._post("/vehicle/odometer", traveled_distance_km=km)
        # 차량은 주행거리를 32비트 실수로 저장한다(23481.2 → 23481.199). 앱 표시 단위(0.1km)보다 훨씬 작은 차이는 같은 값으로 본다.
        self._verify(
            f"주행거리 {km}",
            lambda s: float(s["odometer"]["traveled_distance_km"]),
            km,
            matches=lambda a, e: abs(float(a) - float(e)) <= ODOMETER_TOLERANCE_KM,
        )

    def speed(self, kph: int) -> None:
        """차량 속도(가상 콘솔 controls). 앱은 속도 값을 저장하지 않고 급가속·급감속 횟수만 센다.
        환경 변수 VC_TRACE에 파일 경로를 주면 명령마다 (보낸 시각, 속도, 응답 시간)을 덧붙인다 — 콘솔 지연과
        급가속·급감속 과다 집계의 관계를 실험 기록으로 남기기 위해서다."""
        query = urllib.parse.urlencode({"speed_kph": int(kph)})
        sent = time.time()
        self._request("PUT", f"/vehicle/controls?{query}")
        trace = os.environ.get("VC_TRACE")
        if trace:
            with open(trace, "a") as f:
                f.write(f"{sent:.3f},{int(kph)},{time.time() - sent:.3f}\n")

    def ramp_speed(self, start: int, end: int, kph_per_second: float, hold_s: float = 2.0) -> None:
        """[start]에서 [end]까지 초당 [kph_per_second]km/h로 바꾸고 [hold_s]초 유지.
        콘솔 요청이 늦어져도 밀린 단계를 몰아서 보내지 않는다(몰아 보내면 실제 변화율이 급가속이 된다).
        매번 지금 시각의 이상적인 속도를 계산해 그 값(정수)만 보낸다."""
        direction = 1 if end > start else -1
        duration = abs(end - start) / kph_per_second
        began = time.monotonic()
        sent = start
        while True:
            elapsed = time.monotonic() - began
            ideal = start + direction * kph_per_second * min(elapsed, duration)
            target = int(ideal) if direction > 0 else -int(-ideal)  # 목표를 넘지 않게 0 쪽으로 자름
            if target != sent:
                self.speed(target)
                sent = target
            if elapsed >= duration:
                break
            time.sleep(0.05)
        if sent != end:
            self.speed(end)
        time.sleep(hold_s)

    def odometer_now(self) -> float:
        return float(self.state()["odometer"]["traveled_distance_km"])

    def reset(self) -> None:
        """시나리오 시작 전 기본 상태: 주차, 와이퍼 끔."""
        self.gear("Park")
        self.wiper("Off")


# ---------------------------------------------------------------- 시나리오

@dataclass
class Trip:
    """앱 주행 기록 한 줄(형식 C1, DriveLogCodec과 같은 칸 순서)."""
    start: int
    end: int
    shifts: int
    rain: float | None
    distance: float | None
    accel: int | None
    decel: int | None
    park_dir: str | None
    windows_ms: int | None
    temp: int | None
    fan_max: bool | None
    heat: int | None
    temp_start: int | None
    heat_start: int | None
    vent: int | None
    vent_start: int | None

    @staticmethod
    def parse(line: str) -> "Trip":
        c = line.split("\t")
        f = lambda i: float(c[i]) if c[i] else None  # noqa: E731
        n = lambda i: int(c[i]) if c[i] else None  # noqa: E731
        return Trip(int(c[1]), int(c[2]), int(c[3]), f(4), f(5), n(6), n(7), c[8] or None, n(9), n(10),
                    {"1": True, "0": False}.get(c[11]), n(12), n(13), n(14), n(15), n(16))


def run_trip(console: Console, during: Callable[[Console], None]) -> Trip:
    """P → D → (during) → P 한 번을 실행하고, 앱이 새로 남긴 주행 기록을 돌려준다."""
    before = len(trip_lines())
    console.gear("Drive")
    time.sleep(2)
    during(console)
    console.gear("Park")
    deadline = time.monotonic() + 10
    trip: Trip | None = None
    while time.monotonic() < deadline:
        lines = trip_lines()
        if len(lines) > before:
            trip = Trip.parse(lines[-1])
            break
        time.sleep(0.5)
    if trip is None:
        raise ToolError("주행을 마쳤지만 앱에 기록이 남지 않았습니다(동의·기록 서비스 확인 필요).")
    # 주행거리 신호는 기어보다 몇 초 늦게 와서, 앱이 주차 중에 거리를 고칠 수 있다. 같은 주행이 더 바뀌지 않을 때까지 본다.
    settle_until = time.monotonic() + SETTLE_S
    while time.monotonic() < settle_until:
        time.sleep(0.5)
        latest = next((Trip.parse(l) for l in reversed(trip_lines()) if Trip.parse(l).start == trip.start), trip)
        if latest != trip:
            trip = latest
            settle_until = time.monotonic() + SETTLE_S
    return trip


def scenario_commute(console: Console) -> list[str]:
    # 주행거리는 주행 중에만 바꾼다. 주차 중에 바꾸면 실제 차에서는 일어날 수 없는 상황이 되고,
    # 앱은 그 증가를 직전 주행의 늦은 갱신으로 본다(TripRecorder 참고).
    start_km = console.odometer_now()
    speed = speed_permission_granted()
    trip = run_trip(console, lambda c: (time.sleep(2), c.odometer(round(start_km + 5.2, 1))))
    return check([
        ("거리 5.2km", trip.distance is not None and abs(trip.distance - 5.2) < 0.01, trip.distance),
        ("빗길 0%", trip.rain == 0.0, trip.rain),
        ("주차 기어 반복 0", trip.shifts == 0, trip.shifts),
        # 속도 권한이 있으면 0번, 없으면 0이 아니라 "모름"(None)이어야 한다.
        ("급가속·급감속 " + ("0번" if speed else "모름(권한 없음)"),
         (trip.accel, trip.decel) == ((0, 0) if speed else (None, None)), (trip.accel, trip.decel)),
    ])


def scenario_cabin(console: Console) -> list[str]:
    """출발 때 22도·열선 끔 → 달리다 26도, 열선 2, 창문을 4초 내림 → 주차. 기록에 출발 값과 바뀐 값이 남는지."""
    console.cabin_temp(22)
    console.seat_heat(0)
    console.windows(0)
    def drive(c: Console) -> None:
        time.sleep(1)
        c.cabin_temp(26)
        c.seat_heat(2)
        c.windows(100)
        time.sleep(4)
        c.windows(0)
        time.sleep(1)
    trip = run_trip(console, drive)
    console.cabin_temp(22)
    console.seat_heat(0)
    return check([
        ("출발 때 22도 → 주차 전 26도", (trip.temp_start, trip.temp) == (22, 26), (trip.temp_start, trip.temp)),
        ("출발 때 열선 꺼짐 → 2단", (trip.heat_start, trip.heat) == (0, 2), (trip.heat_start, trip.heat)),
        ("창문을 연 시간 약 4초", trip.windows_ms is not None and 2_500 <= trip.windows_ms <= 6_500, trip.windows_ms),
    ])


def scenario_rain(console: Console) -> list[str]:
    trip = run_trip(console, lambda c: (time.sleep(2), c.wiper("Low"), time.sleep(2)))
    console.wiper("Off")
    # 와이퍼는 주행 중간에 켰으므로 대략 절반
    return check([("빗길 약 50%", trip.rain is not None and 0.2 < trip.rain < 0.8, trip.rain)])


def scenario_parking(console: Console) -> list[str]:
    def struggle(c: Console) -> None:
        for gear in ("Reverse", "Drive", "Reverse"):
            c.gear(gear)
            time.sleep(1)
    trip = run_trip(console, struggle)
    return check([
        ("주차 기어 반복 3회", trip.shifts == 3, trip.shifts),
        ("후면 주차(P 직전 R)", trip.park_dir == "R", trip.park_dir),
    ])


def scenario_harsh(console: Console) -> list[str]:
    """부드러운 가속·감속은 세지 않고, 기준을 넘는 급가속 1번·급감속 1번만 세는지(명세 §15)."""
    def drive(c: Console) -> None:
        c.ramp_speed(0, 40, 5)     # 부드러운 출발(초당 5km/h < 11)
        c.ramp_speed(40, 70, 18)   # 급가속(초당 18km/h: 기준 11 이상, 센서 튐 상한 25 이하)
        c.ramp_speed(70, 20, 4)    # 부드러운 감속(초당 4km/h < 7.5)
        c.ramp_speed(20, 0, 12)    # 급감속(초당 12km/h: 기준 7.5 이상, 상한 40 이하)
    if not speed_permission_granted():
        raise ToolError("차량 속도 권한이 꺼져 있어 급가속·급감속을 셀 수 없습니다. 앱의 「켜기」 버튼이나 설정에서 켜거나 "
                        f"adb shell pm grant --user {DRIVER_USER} {PACKAGE} android.car.permission.CAR_SPEED")
    trip = run_trip(console, drive)
    return check([
        ("급가속 1번", trip.accel == 1, trip.accel),
        ("급감속 1번", trip.decel == 1, trip.decel),
    ])


SCENARIOS: dict[str, Callable[[Console], list[str]]] = {
    "commute": scenario_commute,
    "cabin": scenario_cabin,
    "rain": scenario_rain,
    "parking": scenario_parking,
    "harsh": scenario_harsh,
}

# 차량 속도를 바꾸는 시나리오. 콘솔 웹 페이지가 열려 있으면 결과를 믿을 수 없다.
SPEED_SCENARIOS = {"harsh"}


def check(items: list[tuple[str, bool, object]]) -> list[str]:
    failures = []
    for name, ok, actual in items:
        print(f"    {'통과' if ok else '실패'}  {name} (실제 {actual})")
        if not ok:
            failures.append(name)
    return failures


# ---------------------------------------------------------------- 명령

def require_consent() -> None:
    consent = app_file("consent.txt")
    if consent is None:
        raise ToolError("앱에서 기록 동의가 되어 있지 않습니다. 앱을 열고 '동의하고 시작하기'를 눌러 주세요.")


def cmd_status(_: argparse.Namespace) -> int:
    ensure_device()
    with Console() as console:
        s = console.state()
        print(f"가상 콘솔: 켜짐 · 기어 {s['gear']['actual']['value'] if s['gear'].get('actual') else '모름'}"
              f" · 와이퍼 {s['body']['wipers']['front']['mode']}"
              f" · 주행거리 {s['odometer']['traveled_distance_km']}km")
    consent = app_file("consent.txt")
    print(f"앱 동의: {'버전 ' + consent.split()[0] if consent else '안 됨'} · 주행 기록 {len(trip_lines())}건")
    return 0


def cmd_keepalive(_: argparse.Namespace) -> int:
    ensure_device()
    with Console():
        print("가상 콘솔을 켜 둡니다. 시연이 끝나면 Ctrl+C로 종료하세요.")
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            print("\n종료합니다.")
    return 0


def cmd_scenario(args: argparse.Namespace) -> int:
    ensure_device()
    require_consent()
    names = list(SCENARIOS) if args.name == "all" else [args.name]
    failed: dict[str, list[str]] = {}
    with Console() as console:
        if set(names) & SPEED_SCENARIOS:
            require_no_console_interference(console)
        for name in names:
            print(f"[{name}]")
            console.reset()
            try:
                failures = SCENARIOS[name](console)
            except ToolError as e:
                failures = [str(e)]
                print(f"    실패  {e}")
            if failures:
                failed[name] = failures
        console.reset()
    print(f"\n결과: {len(names) - len(failed)}/{len(names)} 통과")
    return 1 if failed else 0


def cmd_demo(args: argparse.Namespace) -> int:
    ensure_device()
    require_consent()
    adb("logcat", "-c")
    adb("shell", "am", "broadcast", "--user", DRIVER_USER, "-a", f"{PACKAGE}.DEMO_DATA",
        "-n", f"{PACKAGE}/.debug.DemoDataReceiver", "--es", "scenario", args.name)
    time.sleep(2)
    log = [l for l in adb("logcat", "-d", "-s", "Chatoon/Demo").splitlines() if "Chatoon/Demo" in l]
    if not log:
        raise ToolError("시연 데이터 결과를 확인할 수 없습니다(debug 빌드가 설치돼 있는지 확인).")
    message = log[-1].split("Chatoon/Demo:")[-1].strip()
    print(message)
    # 시연 기록과 차의 누적 주행거리를 맞춘다(설정 화면에 '주행 120회 · 누적 0.0 km'가 함께 보이는 모순 방지, 8c X1).
    odometer = re.search(r"odometer ([0-9.]+) km", message)
    if odometer:
        target = float(odometer.group(1))
        with Console() as console:
            now = console.odometer_now()
            # 콘솔은 주행거리를 줄이는 요청을 거절한다(400 traveled_distance_must_not_decrease). 더 크면 그대로 둔다.
            if target > now + ODOMETER_TOLERANCE_KM:
                console.odometer(target)
                print(f"가상 콘솔 누적 주행거리 → {target} km")
            else:
                print(f"가상 콘솔 누적 주행거리는 이미 {now:.1f} km라 그대로 둠(줄일 수 없음)")
    return 0 if message.startswith(("Loaded", "Cleared", "Next navigation request will fail")) else 1


def cmd_set(args: argparse.Namespace) -> int:
    ensure_device()
    with Console() as console:
        setters: dict[str, Callable[[str], None]] = {
            "gear": console.gear,
            "wiper": console.wiper,
            "odometer": lambda v: console.odometer(float(v)),
            "windows": lambda v: console.windows(int(v)),
            "temp": lambda v: console.cabin_temp(float(v)),
            "fan": lambda v: console.fan(int(v)),
            "seatheat": lambda v: console.seat_heat(int(v)),
            "trunk": lambda v: console.trunk(_truthy(v)),
        }
        try:
            setters[args.what](args.value)
        except ValueError as e:
            raise ToolError(f"{args.what}에 쓸 수 없는 값입니다: {args.value}") from e
        print(f"{args.what} = {args.value} (적용 확인됨)")
    return 0


def _pid() -> str:
    return adb("shell", "pidof", PACKAGE, check=False).strip().split(" ")[0]


def _cpu_ticks(pid: str) -> int:
    """/proc/<pid>/stat의 utime+stime(클록 틱)."""
    fields = adb("shell", "cat", f"/proc/{pid}/stat").split(")")[-1].split()
    return int(fields[11]) + int(fields[12])


def _pss_mb(pid: str) -> float:
    """프로세스 번호로 조회한다(패키지 이름은 사용자·시점에 따라 못 찾을 수 있다)."""
    out = adb("shell", "dumpsys", "meminfo", pid)
    for line in out.splitlines():
        parts = line.split()
        if parts[:2] == ["TOTAL", "PSS:"]:
            return int(parts[2]) / 1024
    raise ToolError(f"meminfo에서 PSS를 찾지 못했습니다(pid {pid}).")


def require_recording_service() -> None:
    """기록 서비스가 포그라운드로 떠 있는가(동의해야만 뜬다). release는 파일을 못 읽어 동의 파일 대신 이것으로 확인한다."""
    dump = adb("shell", "dumpsys", "activity", "services", PACKAGE, check=False)
    if "RecordingService" not in dump or "isForeground=true" not in dump:
        raise ToolError("기록 서비스가 떠 있지 않습니다. 앱을 열고 동의해 주세요(release도 측정 가능).")


def cmd_measure(args: argparse.Namespace) -> int:
    ensure_device()
    require_recording_service()
    seconds = args.seconds
    hz = int(adb("shell", "getconf", "CLK_TCK", check=False).strip() or "100")
    cores = int(adb("shell", "nproc", check=False).strip() or "1")
    speed = speed_permission_granted()
    with Console() as console:
        if speed:
            require_no_console_interference(console)
        console.reset()
        console.gear("Drive")
        # 앱을 뒤로: 내비를 앞으로 띄운다(실제 주행과 같은 상태).
        adb("shell", "monkey", "--user", DRIVER_USER, "-c", "android.intent.category.APP_MAPS", "1", check=False)
        time.sleep(3)
        pid = _pid()
        if not pid:
            raise ToolError("앱 프로세스가 없습니다(기록 서비스 확인 필요).")
        start_ticks, start = _cpu_ticks(pid), time.monotonic()
        km = console.odometer_now()
        pss: list[float] = []
        events = 0
        while time.monotonic() - start < seconds:
            # 차툰이 읽는 신호만 바꾼다: 와이퍼·창문·공조 온도·주행거리(전조등·문 잠금·회전은 앱이 읽지 않는다)
            console.wiper("Low" if events % 3 == 0 else "Off")
            console.windows(100 if events % 2 == 0 else 0)
            console.cabin_temp(24 if events % 2 == 0 else 22)
            km = round(km + 0.3, 1)
            console.odometer(km)
            events += 4
            if speed:  # 속도 신호(10Hz)가 흐르며 급가속·급감속 감지 경로가 돈다. 속도 값도 바꿔 실제 주행처럼
                console.speed(40 if events % 2 == 0 else 60)
                events += 1
            pss.append(_pss_mb(pid))
            time.sleep(2)
        elapsed = time.monotonic() - start
        used = (_cpu_ticks(pid) - start_ticks) / hz
        console.windows(0)
        console.cabin_temp(22)
        console.reset()
    cpu_one_core = used / elapsed * 100
    print(f"측정 {elapsed:.0f}초, 차량 신호 변경 {events}회(와이퍼·창문·공조 온도·주행거리{'·속도' if speed else ''}), 앱은 화면 뒤")
    print("속도 권한: " + ("있음 — 10Hz 속도·급가속·급감속 감지 경로 포함" if speed else "없음 — 속도 경로는 측정에 포함되지 않음"))
    print(f"CPU: 코어 1개 기준 {cpu_one_core:.2f}% · 전체 {cores}코어 기준 {cpu_one_core / cores:.2f}%  (기준 5% 미만)")
    print(f"메모리(PSS): 평균 {sum(pss) / len(pss):.1f}MB · 최대 {max(pss):.1f}MB  (기준 400MB 이하)")
    ok = cpu_one_core < 5 and max(pss) <= 400
    print("판정:", "통과" if ok else "기준 초과")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Pleos Virtual Console 시연·검증 도구")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("status", help="콘솔·앱 상태").set_defaults(run=cmd_status)
    sub.add_parser("keepalive", help="콘솔을 켜 둔다").set_defaults(run=cmd_keepalive)
    sc = sub.add_parser("scenario", help="주행 시나리오 실행·검증")
    sc.add_argument("name", choices=["all", *SCENARIOS])
    sc.set_defaults(run=cmd_scenario)
    dm = sub.add_parser("demo", help="시연용 기록 넣기")
    dm.add_argument("name", choices=["commuter", "clear"])
    dm.set_defaults(run=cmd_demo)
    ms = sub.add_parser("measure", help="주행 중 CPU·메모리 측정")
    ms.add_argument("seconds", type=int, nargs="?", default=60)
    ms.set_defaults(run=cmd_measure)
    st = sub.add_parser("set", help="값 하나 바꾸고 적용 확인")
    st.add_argument("what", choices=["gear", "wiper", "odometer", "windows", "temp", "fan", "seatheat", "trunk"])
    st.add_argument("value")
    st.set_defaults(run=cmd_set)
    args = parser.parse_args()
    try:
        return args.run(args)
    except ToolError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
