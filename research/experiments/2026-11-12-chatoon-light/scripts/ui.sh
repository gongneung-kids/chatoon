A=~/Library/Android/sdk/platform-tools/adb; S=${S:-$(mktemp -d)}  # 임시 폴더(캡처·덤프)
tap(){ $A shell uiautomator dump --user 10 /sdcard/ui.xml >/dev/null 2>&1; $A shell cat /sdcard/ui.xml > $S/ui.xml; XY=$(python3 -I - $S/ui.xml "$1" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
ms=re.findall(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'%re.escape(sys.argv[2]),s)
m=ms[-1] if ms else None
print((int(m[0])+int(m[2]))//2,(int(m[1])+int(m[3]))//2) if m else print("")
PY
); echo "$1 -> $XY"; [ -n "$XY" ] && $A shell input tap $XY; }
shot(){ $A exec-out screencap -p > $S/raw.png; sips -c 1210 1682 --cropOffset 90 854 $S/raw.png --out $S/$1.png >/dev/null; }
amb(){ python3 $S/ambient.py "$(git rev-parse --show-toplevel)/tools" | head -1; }
