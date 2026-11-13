"""release 앱 검증용: vc.py의 Console로 주행만 한다(앱 파일은 읽지 않는다)."""
import sys, time
sys.path.insert(0, sys.argv[1])
import vc
kind = sys.argv[2]
with vc.Console() as c:
    c.reset()
    c.gear("Drive"); time.sleep(2)
    if kind == "harsh":
        c.ramp_speed(0, 40, 5); c.ramp_speed(40, 70, 18); c.ramp_speed(70, 20, 4); c.ramp_speed(20, 0, 12)
    elif kind == "parking":
        c.ramp_speed(0, 30, 5); c.ramp_speed(30, 0, 5)
        for g in ("Reverse", "Drive", "Reverse", "Drive", "Reverse"):
            c.gear(g); time.sleep(1)
    c.gear("Park"); time.sleep(4)
    c.reset()
print("주행 끝:", kind)
