"""가상 콘솔의 실내 조명 값을 읽는다(vc.py의 Console 사용)."""
import sys, json
sys.path.insert(0, sys.argv[1])
import vc
with vc.Console() as c:
    st = c.state()
    def find(d, path=""):
        if isinstance(d, dict):
            for k, v in d.items():
                if "ambient" in k.lower(): print(path + k, json.dumps(v, ensure_ascii=False))
                else: find(v, path + k + ".")
    find(st)
