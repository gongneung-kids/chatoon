"""release 앱 최종 확인 주행(앱 파일은 읽지 않는다). 저장소 루트에서:

    python3 research/experiments/2026-11-13-chatoon-release-4/scripts/release_drive4.py

출발 때 공조 22도·열선 끔 → 달리는 동안 비(와이퍼), 창문 전부 내림 70초, 히터 온도 26도로 올림, 열선 2단 켬,
급정거 한 번(초당 10km/h 감속), 후면 주차(R·D·R → P). 끝나면 콘솔 값을 기준값으로 되돌린다.
"""
import sys
import time

sys.path.insert(0, "tools")
import vc  # noqa: E402

with vc.Console() as c:
    c.reset()
    c.cabin_temp(22); c.seat_heat(0); c.fan(3); c.windows(0)
    time.sleep(2)
    c.odometer(round(c.odometer_now() + 0.5, 1))
    c.gear("Drive"); time.sleep(2)
    c.wiper("Low")
    c.ramp_speed(0, 40, 5)
    c.windows(100)
    c.cabin_temp(26)
    c.seat_heat(2)
    c.ramp_speed(40, 60, 5, hold_s=70)  # 창문을 연 채 70초
    c.windows(0)
    c.odometer(round(c.odometer_now() + 12.0, 1))
    c.ramp_speed(60, 20, 10)  # 급정거
    c.ramp_speed(20, 0, 4)
    for g in ("Reverse", "Drive", "Reverse"):
        c.gear(g); time.sleep(1.5)
    c.gear("Park"); time.sleep(4)
    c.wiper("Off"); c.seat_heat(0); c.cabin_temp(22); c.fan(3)
    c.reset()
print("주행 끝")
