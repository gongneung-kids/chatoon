"""콘솔에서 값을 바꾼 뒤 앱이 읽는 값이 따라 바뀌는지 본다."""
import sys, json, time, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "tools"))
sys.path.insert(0, os.path.dirname(__file__))
import vc, probe as run
vc.ensure_device()
KEEP = ("window [", "hvac temp [", "hvac fan [", "seat foreaft [ROW_1_LEFT", "seat occupancy [", "seat heat", "door position [REAR", "door position [ROW_1_LEFT", "seat belt", "ambient", "range")
def show(title):
    print("\n##", title)
    for l in sorted(run.probe("read", wait=4.0)):
        if l.startswith(KEEP) and "caps" not in l:
            print("  ", l)
with vc.Console() as c:
    s = c.state()
    print("콘솔 좌석 점유:", json.dumps(s["seat"].get("occupied"), ensure_ascii=False))
    print("콘솔 조명:", json.dumps(s["body"]["ambient_light"], ensure_ascii=False))
    show("바꾸기 전")
    c._post("/vehicle/body", window_fl=100, window_fr=50, front_wiper="High", trunk=True, door_fl=True)
    c._post("/vehicle/hvac", driver_temp=17, fan_speed=8, driver_seat=3)
    c._post("/vehicle/seat", kind="position", control="slide", area="driver", value=40)
    for area, val in (("rear_left", False), ("rear_right", False), ("passenger", False)):
        c._post("/vehicle/seat", kind="occupied", area=area, value=val)
    c._post("/vehicle/odometer", remaining_range_m=31000)
    time.sleep(1.5)
    s = c.state()
    print("\n콘솔에 넣은 값:", "창문", s["body"]["windows"], "| 온도", s["hvac"]["driver"]["temp_c"], "| 풍량", s["hvac"]["fan_speed"],
          "| 운전석 앞뒤", s["seat"]["positions"]["slide"]["driver"], "| 점유", s["seat"].get("occupied"), "| 트렁크", s["body"]["closures"]["trunk"])
    show("바꾼 뒤")
    # 되돌리기
    c._post("/vehicle/body", window_fl=0, window_fr=0, front_wiper="Off", trunk=False, door_fl=False)
    c._post("/vehicle/hvac", driver_temp=22, fan_speed=3, driver_seat=0)
    c._post("/vehicle/seat", kind="position", control="slide", area="driver", value=0)
    c._post("/vehicle/odometer", remaining_range_m=0)
    print("\n(콘솔 값을 원래대로 되돌림. 좌석 점유는 뒤 두 자리와 조수석을 비운 채로 둠)")
