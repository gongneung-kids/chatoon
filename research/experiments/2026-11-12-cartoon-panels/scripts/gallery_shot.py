"""확인용 화면(CartoonGalleryActivity)을 경우별로 띄워 앱 창만 잘라 저장한다."""
import os, subprocess, sys, time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "kr.chatoon.app"
out_dir = sys.argv[1]
starts = [int(x) for x in sys.argv[2:]] or [0, 2, 4, 6, 8]
os.makedirs(out_dir, exist_ok=True)


def adb(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True, timeout=60)
    return r.stdout if binary else r.stdout.decode(errors="replace")


for start in starts:
    adb("shell", "am", "start", "--user", "10", "-S", "-n", f"{PKG}/.debug.CartoonGalleryActivity", "--ei", "start", str(start), "--ei", "count", "2")
    time.sleep(3.5)
    raw = os.path.join(out_dir, f"raw-{start}.png")
    with open(raw, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))
    crop = os.path.join(out_dir, f"gallery-{start}.png")
    # 앱 창만(1682×1210): 팀 캡처 방법과 같은 자리
    subprocess.run(["sips", "-c", "1210", "1682", "--cropOffset", "90", "854", raw, "--out", crop], capture_output=True)
    os.remove(raw)
    print(crop, os.path.getsize(crop))
