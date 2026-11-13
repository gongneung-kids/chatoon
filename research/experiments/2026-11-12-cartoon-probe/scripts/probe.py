"""탐침 실행기: 방송을 보내고 몇 초 동안의 Chatoon/Probe 로그를 모아 보여 준다."""
import subprocess, sys, time, os
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "kr.chatoon.app"
def adb(*a, timeout=40):
    return subprocess.run([ADB, *a], capture_output=True, text=True, timeout=timeout).stdout
def probe(cmd, extras=(), wait=4.0, tag="Chatoon/Probe"):
    adb("logcat", "-c")
    out = adb("shell", "am", "broadcast", "--user", "10", "-a", PKG + ".VEHICLE_PROBE",
              "-n", PKG + "/.debug.VehicleProbeReceiver", "--es", "cmd", cmd, *extras)
    if "result=" not in out and "Broadcast completed" not in out:
        print("방송 실패:", out.strip()[:200])
    time.sleep(wait)
    log = adb("logcat", "-d", "-v", "brief", "-s", tag + ":V")
    lines = [l.split("): ", 1)[-1] for l in log.splitlines() if tag in l]
    return lines
if __name__ == "__main__":
    cmd = sys.argv[1]
    wait = float(sys.argv[2]) if len(sys.argv) > 2 else 4.0
    for l in probe(cmd, tuple(sys.argv[3:]), wait):
        print(l)
