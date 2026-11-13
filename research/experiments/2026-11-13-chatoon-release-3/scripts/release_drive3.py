"""release 앱 검증용 주행(앱 파일은 읽지 않는다). 저장소 루트에서:

    python3 research/experiments/2026-11-13-chatoon-release-3/scripts/release_drive3.py tools

한 번의 주행에 비(와이퍼), 창문을 전부 내리고 70초, 열선 2단, 공조 17도·풍량 최대, 급정거 한 번, 후면 주차(R·D·R → P)를 넣는다.
끝나면 콘솔 값을 기준값(창문 0·22도·풍량 3·열선 0·와이퍼 끔)으로 되돌린다.
"""
import sys
import time

sys.path.insert(0, sys.argv[1])
import vc  # noqa: E402

with vc.Console() as c:
    c.reset()
    c.gear("Drive"); time.sleep(2)
    c.wiper("Low")
    c.seat_heat(2)
    c.cabin_temp(17)
    c.fan(8)
    c.ramp_speed(0, 40, 5)
    c.windows(100)
    c.ramp_speed(40, 60, 5, hold_s=70)  # 창문을 연 채 70초
    c.windows(0)
    c.ramp_speed(60, 20, 10)  # 급정거: 1초에 10km/h씩
    c.ramp_speed(20, 0, 4)
    for g in ("Reverse", "Drive", "Reverse"):
        c.gear(g); time.sleep(1.5)
    c.gear("Park"); time.sleep(4)
    c.wiper("Off"); c.seat_heat(0); c.cabin_temp(22); c.fan(3)
    c.reset()
print("주행 끝")
