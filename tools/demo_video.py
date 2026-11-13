#!/usr/bin/env python3
"""
차툰 시연 영상을 자동으로 녹화한다(Pleos Connect 에뮬레이터 + 가상 콘솔). 같은 명령으로 언제든 다시 만들 수 있다.

    adb forward tcp:8088 tcp:8088
    python3 tools/demo_video.py                 # → contests/hmg-vehicle-app/submissions/차툰_시연영상.mp4
    python3 tools/demo_video.py --only 3        # 3번 장면까지만(리허설, 앞 장면도 차례로 실행된다)

준비: debug 앱 설치(시연 데이터 넣기는 debug 전용), Google Chrome(자막·신호 그림), ffmpeg.
주의: 앱 데이터를 지우고 처음 실행부터 녹화한다(pm clear). 끝나면 시연 데이터와 녹화 중 주행 두 번이 들어 있는 상태로 남는다.
가상 콘솔의 창문·공조·열선·와이퍼·조명 값을 바꾸고, 끝나면 시작 기준값으로 되돌린다.

화면 배치(1920×1080):
- 왼쪽 위 1600×900: 에뮬레이터 화면 전체(2560×1440을 줄임). 아래 공조 바(열선 아이콘)까지 그대로 보인다.
- 오른쪽 320×900: 가상 콘솔에서 읽은 차량 신호(기어, 속도, 와이퍼, 창문, 공조, 열선, 실내 조명 색). 녹화하는 동안 0.4초마다 읽어
  값이 바뀐 시점에 그림을 바꾼다. 앱이 차를 읽고(창문·열선) 바꾸는(실내 조명) 것을 화면 밖에서도 확인할 수 있게 한다.
- 아래 1920×180: 자막.

- 장면마다 짧은 클립을 따로 녹화한다(screenrecord 3분 제한, 자막 시점 맞추기). 장면 사이의 기다림(창문 1분 등)은 녹화하지 않는다.
- screenrecord는 화면이 바뀔 때만 프레임을 남겨 멈춘 장면이 잘린다. 실제 경과 시간만큼 마지막 프레임을 늘려 자막을 읽을 시간을 둔다.
- 이 ffmpeg에는 글자 필터가 없어, 자막과 신호판은 Chrome으로 그림을 만들어 영상 위에 겹친다.
- 자막에는 화면에서 확인되는 사실만 쓴다. 시각(낮·밤)은 녹화하는 시각에 따라 바뀌므로 쓰지 않는다. 실명·계정은 쓰지 않는다.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

sys.path.insert(0, str(Path(__file__).resolve().parent))
import vc  # noqa: E402  같은 폴더의 가상 콘솔 도구

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "contests" / "hmg-vehicle-app" / "submissions" / "차툰_시연영상.mp4"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT_W, OUT_H = 1920, 1080
SHOT_W, SHOT_H = 1600, 900  # 에뮬레이터 화면(2560×1440)을 줄인 크기
PANEL_W = OUT_W - SHOT_W
CAPTION_H = OUT_H - SHOT_H
FPS = 30
POLL_S = 0.4
RECORD_LAG_S = 0.6  # screenrecord를 띄우고 첫 프레임이 나올 때까지(대략)

# 녹화 전에 맞추고 끝나면 되돌리는 가상 콘솔 값. 조명은 콘솔 기본값(흰색 70%)이다.
BASELINE = dict(windows=0, temp=22, fan=3, seat=0, wiper="Off", ambient=("#ffffff", 70))


# ---------------------------------------------------------------- 기기 조작

def adb(*args: str, check: bool = True) -> str:
    return vc.adb(*args, check=check)


def tap_text(text: str, timeout_s: float = 6.0) -> None:
    """운전자(사용자 10) 화면에서 [text] 글자를 가진 요소의 가운데를 누른다."""
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        adb("shell", "uiautomator", "dump", "--user", vc.DRIVER_USER, "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        m = re.search(r'text="' + re.escape(text) + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if m:
            x = (int(m[1]) + int(m[3])) // 2
            y = (int(m[2]) + int(m[4])) // 2
            adb("shell", "input", "tap", str(x), str(y))
            return
        time.sleep(0.4)
    raise vc.ToolError(f"화면에서 '{text}'을(를) 찾지 못함")


def tap_scrolling(text: str, max_swipes: int = 5) -> None:
    """[text]가 화면 아래에 있어 안 보이면 위로 밀면서 찾은 뒤 누른다(장면 순서·화면 길이가 바뀌어도 깨지지 않게)."""
    for _ in range(max_swipes):
        try:
            tap_text(text, timeout_s=2.0)
            return
        except vc.ToolError:
            swipe_up()
            time.sleep(0.6)
    tap_text(text)


def swipe_up() -> None:
    adb("shell", "input", "swipe", "1700", "1150", "1700", "500", "400")


def scroll_to_top() -> None:
    """앞 장면의 스크롤 위치가 남아 있을 수 있어, 장면 시작 때 맨 위로 올린다."""
    for _ in range(3):
        adb("shell", "input", "swipe", "1700", "400", "1700", "1250", "250")


def launch_app() -> None:
    adb("shell", "am", "start", "--user", vc.DRIVER_USER, "-n", f"{vc.PACKAGE}/.ui.MainActivity")


def wait(seconds: float) -> None:
    time.sleep(seconds)


def set_baseline(c: vc.Console) -> None:
    c.gear("Park")
    c.speed(0)
    c.wiper(BASELINE["wiper"])
    c.windows(BASELINE["windows"])
    c.cabin_temp(BASELINE["temp"])
    c.fan(BASELINE["fan"])
    c.seat_heat(BASELINE["seat"])
    c.ambient(*BASELINE["ambient"])


# ---------------------------------------------------------------- 장면

@dataclass
class Scene:
    caption: str
    action: Callable[[vc.Console], None]
    sub: str = ""
    # 녹화를 멈춘 뒤 다음 장면 전에 할 일(영상에 넣지 않음). 창문을 1분 넘게 열어 두는 기다림 같은 것.
    after: Callable[[vc.Console], None] | None = None
    timeline: list = field(default_factory=list)


def scene_list() -> list[Scene]:

    def consent(_: vc.Console) -> None:
        wait(7)  # 처음 소개: 무엇을 하는 앱인지, 쓰는 법 세 단계, 예시 네 컷
        tap_text("시작하기")
        wait(5)
        tap_text("동의하고 시작하기")
        wait(1.5)
        try:
            tap_text("허용", timeout_s=3)  # 알림 권한(이미 허용했으면 뜨지 않음)
        except vc.ToolError:
            pass
        wait(3)

    def load_history(_: vc.Console) -> None:
        # 지난 화를 보여 주려면 몇 주치 기록이 있어야 한다. 시연 데이터(약 12주, 평일 출퇴근)를 넣는다.
        vc.cmd_demo(argparse.Namespace(name="commuter"))
        wait(2)

    def drive(c: vc.Console) -> None:
        start_km = c.odometer_now()
        c.gear("Drive")
        wait(1)
        c.ramp_speed(0, 40, 8, hold_s=0.5)  # 초당 8km/h: 급가속(11) 아래
        # 주행거리는 달리는 동안에만 늘린다(주차 중에 늘리면 직전 주행의 늦은 갱신으로 본다).
        # 이 주행은 1분 남짓이라 시속 40km로 달린 만큼(약 0.9km)만 늘린다. 화면에 「1분 운전 · 0.9km」로 나온다.
        c.odometer(round(start_km + 0.9, 1))
        c.wiper("Low")
        wait(1.5)
        c.windows(100)
        wait(1.5)
        c.seat_heat(2)
        wait(4)

    def keep_windows_open(_: vc.Console) -> None:
        wait(58)  # 창문을 1분 넘게 열고 달려야 "창문을 내리고 달린 날"이다(톨게이트에서 잠깐 연 것은 세지 않음)

    def park(c: vc.Console) -> None:
        c.windows(0)
        c.ramp_speed(40, 0, 5, hold_s=0.5)  # 초당 5km/h로 천천히 섬: 급감속(7.5) 아래
        c.gear("Park")
        wait(7)  # 이번 화가 뜨고 차가 한마디를 읽는다
        swipe_up()  # 아래 두 컷까지
        wait(5)

    def answer(_: vc.Console) -> None:
        scroll_to_top()
        wait(1.5)
        tap_text("그냥 바람 쐬러")
        wait(7)

    def light(_: vc.Console) -> None:
        tap_scrolling("켜기")
        wait(3)
        scroll_to_top()
        wait(4)

    def dry_up(c: vc.Console) -> None:
        c.wiper("Off")
        c.seat_heat(0)
        wait(1)

    def brake(c: vc.Console) -> None:
        start_km = c.odometer_now()
        c.gear("Drive")
        wait(1)
        c.ramp_speed(0, 50, 8, hold_s=1.5)  # 초당 8km/h: 급가속(11) 아래
        c.odometer(round(start_km + 0.2, 1))  # 10초 남짓 달린 만큼. 화면에 「1분 운전 · 0.2km」로 나온다
        c.ramp_speed(50, 0, 25, hold_s=1)  # 초당 25km/h로 줄임: 급감속(7.5) 넘음
        c.gear("Park")
        wait(7)

    def jolt_answer(_: vc.Console) -> None:
        scroll_to_top()
        tap_text("앞차가 갑자기")
        wait(7)

    def past(_: vc.Console) -> None:
        tap_text("지난 화")
        scroll_to_top()
        wait(6)  # 맨 위: 이번 달 운전한 시간이 몇 화로 남았는지, 주별 묶음
        swipe_up()
        wait(5)

    def settings(_: vc.Console) -> None:
        tap_text("설정")
        wait(2)
        # 차량 연결 상태의 창문·공조·열선 줄까지 보이게 아래로 내린다(화면 끝까지 내려도 된다).
        for _ in range(3):
            swipe_up()
            wait(0.8)
        wait(6)

    return [
        Scene("처음 열면 무엇을 하는 앱인지(쓰는 법 세 단계, 예시 네 컷) 보여 주고, 기억하는 것과 기억하지 않는 것을 확인한 뒤 동의합니다", consent,
              "동의 전에는 아무것도 기록하지 않습니다 · 기억하지 않는 것: 위치·경로·영상", after=load_history),
        Scene("그냥 지나갈 뻔한 운전 시간: 비가 와서 와이퍼를 켜고, 창문을 내리고, 열선을 켠 순간들", drive,
              "가상 콘솔로 재현 · 주행 중(D단)에는 Pleos가 앱 화면을 가립니다 · 오른쪽은 차량 신호", after=keep_windows_open),
        Scene("세우자마자 그 순간들이 네 컷으로 남습니다. 차가 한마디를 소리로 읽어 줍니다", park,
              "컷마다 '근거'에 어느 차량 신호에서 나온 순간인지 적습니다(와이퍼·창문 위치·시트 열선)"),
        Scene("차가 '어디 가시는 길이었나요?' 하고 묻고, 답을 이유로 삼아 그날의 이야기로 다시 씁니다", answer,
              "보기·직접 적기·말로 답하기(기기 안 받아쓰기) · 주인의 말은 대사로 옮기지 않고 근거에 출처로만"),
        Scene("'실내 조명 연출'을 켜면, 그 화를 보는 동안 실내 조명이 그날의 색으로 바뀝니다", light,
              "오른쪽 '실내 조명'이 바뀝니다 · 다른 화면으로 가면 바꾸기 전 값으로 되돌립니다", after=dry_up),
        Scene("급하게 선 순간도 남습니다: 속도가 1초에 시속 7.5km 넘게 줄면 차가 놀란 컷이 되고, 조명이 잠깐 주황으로 깜빡입니다", brake,
              "속도 값은 저장하지 않고 횟수만 · 꾸짖거나 점수를 매기지 않습니다"),
        Scene("차가 '괜찮으세요?' 하고 묻고, 답한 이유로 그 순간의 이야기를 마저 씁니다", jolt_answer,
              "보기는 앞차가 갑자기 · 누가 끼어들어서 · 신호가 바뀌어서 · 한눈팔다가"),
        Scene("지난 화: 운전할 때마다 한 화씩 쌓여, 지나간 운전 시간을 다시 넘겨 볼 수 있습니다", past,
              "별일 없던 날도 몇 번째 주행, 며칠 만의 비처럼 지난 기록에서 센 사실로 남김 · 지난 기록은 시연 데이터(약 12주)"),
        Scene("설정: 차툰이 읽는 차량 신호를 그대로 보여 줍니다. 차를 바꾸는 것은 켜 둔 실내 조명 연출 하나뿐입니다", settings,
              "기록은 이 차 안에만 암호화해 저장 · '기록 끄고 모두 삭제'로 언제든 지움"),
    ]


# ---------------------------------------------------------------- 녹화

def signal_values(state: dict) -> tuple:
    """신호판에 쓸 값(글자). 같은 값이면 같은 그림을 쓴다. 속도는 5km/h 단위로 묶어 그림 수를 줄인다."""
    body, hvac = state["body"], state["hvac"]
    gear = {"Park": "P", "Reverse": "R", "Neutral": "N", "Drive": "D"}.get(state["gear"]["actual"]["value"], "?")
    speed = int(state["controls"]["speed_kph"]) // 5 * 5
    wiper = body["wipers"]["front"]["mode"]
    window = max(int(v) for v in body["windows"].values())
    temp = float(hvac["driver"]["temp_c"])
    seat = int(hvac["driver"]["seat_temp"])
    light = body["ambient_light"]
    return (gear, speed, wiper, window, f"{temp:g}", int(hvac["fan_speed"]), seat,
            light["mode"], light["color"].lower(), int(light["brightness_pct"]))


def poll_signals(console: vc.Console, t0: float, stop: threading.Event, out: list) -> None:
    while not stop.is_set():
        try:
            values = signal_values(console.state())
            if not out or out[-1][1] != values:
                out.append((max(0.0, time.monotonic() - t0), values))
        except (vc.ToolError, KeyError, ValueError):
            pass
        stop.wait(POLL_S)


def record(scene: Scene, console: vc.Console, index: int, work: Path) -> tuple[Path, float]:
    remote = f"/sdcard/demo_{index:02d}.mp4"
    adb("shell", "rm", "-f", remote, check=False)
    proc = subprocess.Popen([vc.adb_path(), "shell", "screenrecord", "--bit-rate", "16000000", "--time-limit", "170", remote])
    t0 = time.monotonic() + RECORD_LAG_S
    stop = threading.Event()
    poller = threading.Thread(target=poll_signals, args=(console, t0, stop, scene.timeline), daemon=True)
    poller.start()
    time.sleep(1.0)  # 녹화가 시작될 때까지
    try:
        scene.action(console)
    finally:
        time.sleep(0.8)
        duration = time.monotonic() - t0
        stop.set()
        poller.join(timeout=3)
        adb("shell", "pkill", "-INT", "screenrecord", check=False)
        proc.wait(timeout=15)
    time.sleep(0.5)
    local = work / f"raw_{index:02d}.mp4"
    adb("pull", remote, str(local))
    adb("shell", "rm", "-f", remote, check=False)
    return local, duration


# ---------------------------------------------------------------- 자막·신호판·표지 그림

FONT = '"Apple SD Gothic Neo","Noto Sans KR",sans-serif'

CAPTION_HTML = """<!doctype html><meta charset="utf-8"><style>
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden;background:#0e0f11}}
.bar{{position:absolute;inset:0;display:flex;flex-direction:column;justify-content:center;padding:0 48px;
font-family:{font};color:#fff;word-break:keep-all}}
.main{{font-size:38px;font-weight:600;line-height:1.3}}
.sub{{font-size:26px;color:#b9c2cc;margin-top:10px}}
</style><div class="bar"><div class="main">{main}</div>{sub}</div>"""

PANEL_HTML = """<!doctype html><meta charset="utf-8"><style>
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden;background:#16181c}}
.p{{position:absolute;left:0;top:0;width:{w}px;height:{h}px;padding:30px 26px;font-family:{font};color:#e8eaed;box-sizing:border-box}}
.t{{font-size:24px;font-weight:700;margin-bottom:4px}} .s{{font-size:17px;color:#8f969e;margin-bottom:22px}}
.r{{display:flex;justify-content:space-between;align-items:center;padding:13px 0;border-top:1px solid #2a2e35;font-size:21px}}
.k{{color:#a8afb7}} .v{{font-weight:700;font-size:23px}} .on{{color:#ffd54a}}
.sw{{margin-top:12px;height:120px;border-radius:14px;border:2px solid #3a3f47;display:flex;align-items:flex-end;padding:10px 14px;
box-sizing:border-box;font-size:18px;font-weight:600}}
</style><div class="p"><div class="t">차량 신호</div><div class="s">가상 콘솔에서 읽은 값</div>{rows}
<div class="r" style="border-bottom:none"><span class="k">실내 조명</span><span class="v">{light}</span></div>
<div class="sw" style="background:{color};color:{ink}">{color_label}</div></div>"""

CARD_HTML = """<!doctype html><meta charset="utf-8"><style>
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden;background:#0e0f11}}
.c{{position:absolute;inset:0;display:flex;flex-direction:column;justify-content:center;padding:0 150px;
font-family:{font};color:#f2f3f5;word-break:keep-all}}
h1{{font-size:112px;margin:0 0 30px;font-weight:700}} p{{font-size:44px;margin:0 0 18px;line-height:1.35}} .m{{color:#9aa0a6;font-size:32px}}
</style><div class="c">{body}</div>"""


def chrome_png(markup: str, width: int, height: int, out: Path) -> None:
    page = out.with_suffix(".html")
    page.write_text(markup, encoding="utf-8")
    subprocess.run(
        [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
         f"--window-size={width},{height}", f"--screenshot={out}", page.as_uri()],
        check=True, capture_output=True, timeout=60,
    )
    if not out.exists():
        raise vc.ToolError(f"그림을 만들지 못함: {out.name}")


def caption_png(scene: Scene, out: Path) -> None:
    sub = f'<div class="sub">{html.escape(scene.sub)}</div>' if scene.sub else ""
    chrome_png(CAPTION_HTML.format(w=OUT_W, h=CAPTION_H, font=FONT, main=html.escape(scene.caption), sub=sub), OUT_W, CAPTION_H, out)


def panel_png(values: tuple, out: Path) -> None:
    gear, speed, wiper, window, temp, fan, seat, light_mode, color, brightness = values
    base_temp = f"{BASELINE['temp']:g}"

    def row(key: str, value: str, lit: bool = False) -> str:
        return f'<div class="r"><span class="k">{key}</span><span class="v{" on" if lit else ""}">{html.escape(value)}</span></div>'

    rows = "".join([
        row("기어", gear, gear != "P"),
        row("속도", f"{speed} km/h", speed > 0),
        row("와이퍼", "켜짐" if wiper != "Off" else "꺼짐", wiper != "Off"),
        row("창문", f"{window}% 내림" if window > 0 else "닫힘", window > 0),
        row("공조 설정", f"{temp}도 · 바람 {fan}", temp != base_temp),
        row("운전석 열선", f"{seat}단" if seat > 0 else ("통풍" if seat < 0 else "꺼짐"), seat != 0),
    ])
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    ink = "#111" if (r * 299 + g * 587 + b * 114) / 1000 > 140 else "#fff"
    chrome_png(PANEL_HTML.format(w=PANEL_W, h=SHOT_H, font=FONT, rows=rows,
                                 light=f"{brightness}%" if light_mode != "Off" else "꺼짐",
                                 color=color, ink=ink, color_label=color), PANEL_W, SHOT_H, out)


def panel_track(scene: Scene, index: int, duration: float, work: Path, cache: dict) -> Path:
    """신호판 그림을 값이 바뀐 시점마다 바꾸는 이미지 목록(ffmpeg concat 형식)."""
    timeline = scene.timeline
    if not timeline:
        raise vc.ToolError("녹화하는 동안 가상 콘솔 값을 한 번도 읽지 못함(콘솔 연결 확인)")
    entries = []
    for k, (t, values) in enumerate(timeline):
        if values not in cache:
            png = work / f"panel_{len(cache):03d}.png"
            panel_png(values, png)
            cache[values] = png
        start = 0.0 if k == 0 else t
        end = timeline[k + 1][0] if k + 1 < len(timeline) else duration
        if end > start:
            entries.append((cache[values], end - start))
    listing = work / f"panel_{index:02d}.txt"
    lines = [f"file '{png}'\nduration {seconds:.3f}\n" for png, seconds in entries]
    lines.append(f"file '{entries[-1][0]}'\n")  # concat 형식은 마지막 그림을 한 번 더 적어야 마지막 길이를 지킨다
    listing.write_text("".join(lines), encoding="utf-8")
    return listing


def card_png(lines: list[tuple[str, str]], out: Path) -> None:
    """표지·끝 카드. 줄 종류: "h1"(제목), "p"(본문), "m"(작은 회색 글)."""
    markup = {"h1": "<h1>{}</h1>", "p": "<p>{}</p>", "m": '<p class="m">{}</p>'}
    body = "".join(markup[kind].format(html.escape(text)) for kind, text in lines)
    chrome_png(CARD_HTML.format(w=OUT_W, h=OUT_H, font=FONT, body=body), OUT_W, OUT_H, out)


# ---------------------------------------------------------------- 편집

def ffmpeg(*args: str) -> None:
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def compose(raw: Path, panel: Path, caption: Path, duration: float, out: Path) -> None:
    # 화면이 멈춘 뒤의 시간도 영상에 남도록 마지막 프레임을 늘리고, 정확히 [duration]초로 자른다.
    ffmpeg(
        "-i", str(raw), "-f", "concat", "-safe", "0", "-i", str(panel), "-loop", "1", "-i", str(caption),
        "-filter_complex",
        f"[0:v]fps={FPS},tpad=stop_mode=clone:stop_duration=30,scale={SHOT_W}:{SHOT_H},setsar=1,"
        f"pad={OUT_W}:{OUT_H}:0:0:color=0x0e0f11[s];"
        f"[1:v]crop={PANEL_W}:{SHOT_H}:0:0,fps={FPS},tpad=stop_mode=clone:stop_duration=30,setsar=1[p];"
        f"[s][p]overlay={SHOT_W}:0:shortest=0[sp];"
        f"[sp][2:v]overlay=0:{SHOT_H}:shortest=0,format=yuv420p[o]",
        "-map", "[o]", "-t", f"{duration:.2f}", "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "20",
        "-an", str(out),
    )


def still(image: Path, seconds: float, out: Path) -> None:
    ffmpeg("-loop", "1", "-i", str(image), "-t", str(seconds), "-vf", f"scale={OUT_W}:{OUT_H},format=yuv420p,fps={FPS}",
           "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-an", str(out))


def concat(parts: list[Path], out: Path, work: Path) -> None:
    listing = work / "parts.txt"
    listing.write_text("".join(f"file '{p}'\n" for p in parts), encoding="utf-8")
    ffmpeg("-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", "-movflags", "+faststart", str(out))


# ---------------------------------------------------------------- 실행

def prepare(console: vc.Console) -> None:
    vc.ensure_device()
    for tool in ("ffmpeg", "ffprobe"):
        if shutil.which(tool) is None:
            raise vc.ToolError(f"{tool}가 필요합니다")
    if not Path(CHROME).exists():
        raise vc.ToolError("Google Chrome이 필요합니다(자막 그림)")
    adb("shell", "cmd", "uimode", "night", "no", check=False)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    set_baseline(console)
    adb("shell", "pm", "clear", "--user", vc.DRIVER_USER, vc.PACKAGE)
    # 급하게 선 날 장면에 속도가 필요하다. 권한 설명 창은 영상 길이 때문에 녹화하지 않고 미리 허용해 둔다.
    adb("shell", "pm", "grant", "--user", vc.DRIVER_USER, vc.PACKAGE, "android.car.permission.CAR_SPEED", check=False)
    launch_app()
    time.sleep(3)
    launch_app()  # 설치·초기화 직후에는 첫 실행이 다른 앱(내비) 뒤로 밀릴 때가 있다
    time.sleep(3)


def main() -> int:
    parser = argparse.ArgumentParser(description="차툰 시연 영상 자동 녹화")
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--keep", type=Path, help="중간 파일을 남길 폴더(확인용)")
    parser.add_argument("--only", type=int, help="앞에서부터 이 번호의 장면까지만 녹화(리허설)")
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory() as tmp:
            # Chrome에 file:// 주소로 넘기므로 절대 경로여야 한다(--keep에 상대 경로를 주면 실패하던 문제)
            work = Path(args.keep).resolve() if args.keep else Path(tmp)
            work.mkdir(parents=True, exist_ok=True)
            parts = []
            card_png([("h1", "차툰"), ("p", "한두 시간 운전하고 나면, 그 시간은 그냥 이동한 시간으로 사라집니다."),
                      ("p", "창문을 연 순간, 비가 와 와이퍼를 켠 순간, 급하게 선 순간. 차가 본 그 시간을 네 컷 만화로 남깁니다."),
                      ("m", "Pleos Connect v2.1.0 에뮬레이터에서 실제로 동작하는 화면 · 주행은 가상 콘솔로 재현")], work / "title.png")
            still(work / "title.png", 5, work / "part_00.mp4")
            parts.append(work / "part_00.mp4")
            scenes = scene_list()[: args.only] if args.only else scene_list()
            cache: dict = {}
            with vc.Console() as console:
                prepare(console)
                try:
                    for i, scene in enumerate(scenes, start=1):
                        print(f"[{i}] {scene.caption}", flush=True)
                        caption_png(scene, work / f"cap_{i:02d}.png")
                        raw, duration = record(scene, console, i, work)
                        if scene.after:
                            scene.after(console)
                        panel = panel_track(scene, i, duration, work, cache)
                        out = work / f"part_{i:02d}.mp4"
                        compose(raw, panel, work / f"cap_{i:02d}.png", duration, out)
                        parts.append(out)
                finally:
                    set_baseline(console)
            card_png([("h1", "차툰"),
                      ("p", "운전할 때마다 한 화씩 쌓여, 날아가던 운전 시간이 볼 수 있는 기억으로 남습니다."),
                      ("p", "지금은 차 안에서만 그립니다(서버·생성형 AI 없음) · 위치·경로·카메라 없음 · 기록은 차 안에서 암호화"),
                      ("p", "차를 바꾸는 것은 켜 둔 실내 조명 연출 하나뿐, 나머지는 읽기만 합니다"),
                      ("m", "Pleos Vehicle SDK(기어·와이퍼·창문·공조·시트 열선·실내 조명) · TextToSpeech·SpeechToText 기기 안 모드")],
                     work / "end.png")
            still(work / "end.png", 5, work / "part_99.mp4")
            parts.append(work / "part_99.mp4")
            args.out.parent.mkdir(parents=True, exist_ok=True)
            concat(parts, args.out, work)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(args.out)],
                               capture_output=True, text=True, check=True)
        seconds = float(json.loads(probe.stdout)["format"]["duration"])
        print(f"만듦: {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out} ({seconds:.0f}초)")
        return 0
    except vc.ToolError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
