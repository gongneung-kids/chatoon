"""vc.py의 카툰 신호 명령이 실제 가상 콘솔에서 되는지 본다. 값을 바꿨다가 처음 값으로 되돌린다.
기어·속도·와이퍼는 건드리지 않는다.

실행(저장소 루트에서, 에뮬레이터 실행 중): python3 research/experiments/2026-11-12-cartoon-signals-vc/check.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "tools"))
import vc


def snapshot(state: dict) -> dict:
    return {
        "windows": state["body"]["windows"],
        "driver_temp_c": state["hvac"]["driver"]["temp_c"],
        "fan_speed": state["hvac"]["fan_speed"],
        "driver_seat_temp": state["hvac"]["driver"].get("seat_temp"),
        "trunk": state["body"]["closures"]["trunk"],
    }


vc.ensure_device()
failures = 0
with vc.Console() as console:
    before = snapshot(console.state())
    print("처음:", json.dumps(before, ensure_ascii=False))

    steps = [
        ("창문 100", lambda: console.windows(100)),
        ("창문 50", lambda: console.windows(50)),
        ("창문 0", lambda: console.windows(0)),
        ("공조 온도 17", lambda: console.cabin_temp(17)),
        ("공조 온도 26.5", lambda: console.cabin_temp(26.5)),
        ("풍량 8", lambda: console.fan(8)),
        ("풍량 1", lambda: console.fan(1)),
        ("시트 열선 3", lambda: console.seat_heat(3)),
        ("시트 통풍 -2", lambda: console.seat_heat(-2)),
        ("트렁크 열림", lambda: console.trunk(True)),
        ("트렁크 닫힘", lambda: console.trunk(False)),
    ]
    for name, run in steps:
        try:
            run()
            print(f"  됨  {name}  →  {json.dumps(snapshot(console.state()), ensure_ascii=False)}")
        except vc.ToolError as error:
            failures += 1
            print(f"  안 됨  {name}  →  {error}")

    # 처음 값으로 되돌린다. 처음 값이 범위 밖이면(콘솔 기본값이 도구의 범위와 다를 때) 그 사실을 알린다.
    restore = [
        ("창문", lambda: console.windows(int(max(before["windows"].values())))) if len(set(before["windows"].values())) == 1 else None,
        ("공조 온도", lambda: console.cabin_temp(float(before["driver_temp_c"]))),
        ("풍량", lambda: console.fan(int(before["fan_speed"]))),
        ("시트 열선·통풍", lambda: console.seat_heat(int(before["driver_seat_temp"] or 0))),
        ("트렁크", lambda: console.trunk(bool(before["trunk"]))),
    ]
    for item in restore:
        if item is None:
            print("  창문 네 곳의 처음 값이 서로 달라 한꺼번에 되돌리지 못함")
            failures += 1
            continue
        name, run = item
        try:
            run()
        except vc.ToolError as error:
            failures += 1
            print(f"  되돌리지 못함  {name}  →  {error}")
    after = snapshot(console.state())
    print("끝:", json.dumps(after, ensure_ascii=False))
    print("처음과 같음" if after == before else "처음과 다름")
    if after != before:
        failures += 1
print(f"실패 {failures}건")
sys.exit(1 if failures else 0)
