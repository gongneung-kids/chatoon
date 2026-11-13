"""화면 글자를 찾아 누른다(운전자 사용자 10). 저장소 루트에서: python3 <이 파일> "글자" [글자…]"""
import re, sys, time
sys.path.insert(0, "tools")
import vc
for text in sys.argv[1:]:
    for _ in range(12):
        vc.adb("shell", "uiautomator", "dump", "--user", vc.DRIVER_USER, "/sdcard/ui.xml", check=False)
        xml = vc.adb("shell", "cat", "/sdcard/ui.xml", check=False)
        m = re.search(r'text="' + re.escape(text) + r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if m:
            x, y = (int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2
            vc.adb("shell", "input", "tap", str(x), str(y)); print("누름:", text, x, y); break
        time.sleep(0.5)
    else:
        print("못 찾음:", text); sys.exit(1)
    time.sleep(1.5)
