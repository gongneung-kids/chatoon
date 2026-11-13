"""창문·공조·열선이 주행 기록에 남는지 가상 콘솔로 확인한다(4d, 2026-11-13)."""
import sys, time
sys.path.insert(0, "tools")
import vc

with vc.Console() as c:
    c.gear("Park"); c.windows(0); c.cabin_temp(22); c.fan(3); c.seat_heat(0)
    time.sleep(2)
    before = len(vc.trip_lines())
    c.gear("Drive")
    time.sleep(3)
    c.seat_heat(2)
    c.cabin_temp(17); c.fan(8)
    c.windows(100)
    time.sleep(75)
    c.windows(0)
    time.sleep(3)
    c.gear("Park")
    time.sleep(4)
    lines = vc.trip_lines()
    print("new trips:", len(lines) - before)
    print(lines[-1].replace("\t", " | "))
    # 되돌리기
    c.cabin_temp(22); c.fan(3); c.seat_heat(0)
