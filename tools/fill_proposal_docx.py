#!/usr/bin/env python3
"""
기획서 초안(Markdown)을 공식 접수양식 DOCX 02장의 1~8번 칸에 채운다. 원본은 기획서.md 하나다.

    python3 tools/fill_proposal_docx.py            # 기본 경로로 만들기
    python3 tools/fill_proposal_docx.py --out x.docx

- 01장(참가신청서)·03장(동의서)·04장(서명)은 실명·연락처·서명이 들어가므로 건드리지 않는다.
- 02장의 "앱명 / 프로젝트명" 줄과 1~8번 표 칸만 바꾼다. 칸 안의 입력 컨트롤 이름은 예전 양식 제목이 남아 있어
  보이는 제목과 다르므로, 이름이 아니라 02장 안 표의 순서로 채운다(1번 표 = 1. 앱 한 줄 소개 …).
- Markdown 부분 집합만 다룬다: ## 항목 제목, **굵게**, `코드`(그대로 글자), [글](주소)(글만), - 글머리표, 1. 번호 목록,
  | 표 |, > 인용(작은 회색 글). `근거:`로 시작하는 줄은 팀 내부 메모라 뺀다.
- "## 별지. <제목>" 절이 있으면 02장 끝 안내 문단 뒤 새 쪽에 별지로 넣는다(양식 안내: "필요 시 별지를 추가할 수 있습니다").
- 7번 칸에는 assets/screens의 앱 화면을 2열로 넣는다. 팀이 정해야 하는 자리(괄호 안 "확정", "제작", "확인 필요", <팀명>, ⟦제출 전에 채울 곳⟧)는 노란색으로 표시한다.

양식 파일은 주최 측 자료다. 압축을 풀 때 링크 항목은 지우고, 원본은 바꾸지 않는다.
"""
from __future__ import annotations

import argparse
import html
import re
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTEST = ROOT / "contests" / "hmg-vehicle-app"
TEMPLATE = CONTEST / "assets" / "공모전_접수양식_개인정보동의서.docx"
DRAFT = CONTEST / "기획서.md"
SCREENS = CONTEST / "assets" / "screens"
OUT = CONTEST / "submissions" / "접수양식_차툰_02장작성.docx"

# 7번 칸에 2열로 넣을 앱 화면. 본문(3번·5번)에 이미 넣은 그림은 여기 다시 넣지 않는다.
# 화면이 준비되면 (파일 이름, 설명)을 더한다. 비어 있으면 7번에 화면 표를 넣지 않는다.
SCREEN_ORDER: list[tuple[str, str]] = [
    ("chatoon-today.png", "주인이 답한 행선지가 그림이 된 화: 「어디 가시는 길이었나요?」에 「병원」이라고 답하자 마지막 컷이 병원 앞이 되고 차가 「얼른 나아요」. 주차 컷은 R·D·R 화살표와 후면 주차"),
    ("chatoon-driving.png", "D단으로 바꾸면 Pleos가 앱 화면을 가림: 달리는 동안에는 만화도 질문도 보이지 않음"),
]

CELL_WIDTH = 9866 - 110 * 2  # 양식 칸 폭에서 좌우 여백을 뺀 값(dxa)
BODY_SZ = 19  # 9.5pt
SMALL_SZ = 16  # 8pt
HEAD_COLOR = "16324F"
BORDER = "8394A5"
FOOTER_CLEARANCE = 420  # 바닥글 위치에서 본문 끝까지 띄울 거리(dxa): 바닥글 한 줄 약 10pt(200) + 여유
BULLET_NUM_ID = 90
PLACEHOLDER = re.compile(r"(\([^)]*(?:확정|제작|확인 필요)[^)]*\)|<팀명>|⟦[^⟧]*⟧)")


# ---------------------------------------------------------------- Markdown → 블록

def split_sections(md: str) -> dict[int | str, list[str]]:
    sections: dict[int | str, list[str]] = {}
    current = None
    for line in md.splitlines():
        m = re.match(r"^## (\d)\. ", line)
        if m:
            current = int(m.group(1))
            sections[current] = []
            continue
        if line.startswith("## 별지. "):
            current = "annex"
            sections[current] = [f"**{line[3:].strip()}**"]
            continue
        if line.startswith("## ") or line.startswith("# "):
            current = None
            continue
        if current is not None:
            sections[current].append(line)
    return sections


def blocks(lines: list[str]):
    """줄 목록을 (종류, 내용) 블록으로. 종류: p, bullet, num, table, quote, src."""
    out = []
    i = 0
    in_sources = False
    while i < len(lines):
        line = lines[i].rstrip()
        # "근거: `저장소 경로` …"는 팀 내부 메모라 뺀다(심사 자료에는 저장소 경로가 필요 없다).
        # 본문의 "근거: 국내 탑재 대수는 …"처럼 경로로 시작하지 않는 근거 설명은 그대로 둔다.
        s = re.sub(r"\s*근거: `.*$", "", line.strip())
        if not s:
            i += 1
            continue
        if s.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-{3,}:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            out.append(("table", rows))
            continue
        if s.startswith(">"):
            out.append(("quote", s.lstrip("> ").strip()))
            i += 1
            continue
        if re.match(r"^\*\*출처", s):
            in_sources = True
            out.append(("p", s))
            i += 1
            continue
        if re.match(r"^\*\*", s) and not re.match(r"^\*\*출처", s):
            in_sources = False
        img = re.match(r"^!\[([^\]]*)\]\(([^)]+)\)$", s)
        if img:
            out.append(("image", (img.group(1), img.group(2))))
            i += 1
            continue
        m = re.match(r"^(\d+)\. (.*)", s)
        if m:
            out.append(("src" if in_sources else "num", s))
            i += 1
            continue
        if s.startswith("- "):
            out.append(("bullet", s[2:]))
            i += 1
            continue
        out.append(("p", s))
        i += 1
    return out


# ---------------------------------------------------------------- 글자 → runs

def runs(text: str, sz: int = BODY_SZ, color: str | None = None, bold: bool = False) -> str:
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)  # 링크는 글만
    text = text.replace("`", "")
    parts = re.split(r"(\*\*[^*]+\*\*)", text)
    xml = []
    for part in parts:
        if not part:
            continue
        b = bold
        if part.startswith("**") and part.endswith("**"):
            part = part[2:-2]
            b = True
        for seg in PLACEHOLDER.split(part):
            if seg:
                xml.append(run(seg, sz, color, b, highlight=PLACEHOLDER.fullmatch(seg) is not None))
    return "".join(xml)


def run(text: str, sz: int, color: str | None, bold: bool, highlight: bool = False) -> str:
    props = []
    if bold:
        props.append("<w:b/><w:bCs/>")
    if color:
        props.append(f'<w:color w:val="{color}"/>')
    props.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
    if highlight:
        props.append('<w:highlight w:val="yellow"/>')
    props.append('<w:lang w:eastAsia="ko-KR"/>')
    return f'<w:r><w:rPr>{"".join(props)}</w:rPr><w:t xml:space="preserve">{html.escape(text, quote=False)}</w:t></w:r>'


def para(inner: str, *, before: int = 0, after: int = 60, ind: str = "", num: bool = False, keep_next: bool = False) -> str:
    ppr = []
    if keep_next:
        ppr.append("<w:keepNext/>")
    if num:
        ppr.append(f'<w:numPr><w:ilvl w:val="0"/><w:numId w:val="{BULLET_NUM_ID}"/></w:numPr>')
    ppr.append(f'<w:spacing w:before="{before}" w:after="{after}" w:line="276" w:lineRule="auto"/>')
    if ind:
        ppr.append(ind)
    return f"<w:p><w:pPr>{''.join(ppr)}</w:pPr>{inner}</w:p>"


def table(rows: list[list[str]]) -> str:
    cols = max(len(r) for r in rows)
    # 열 폭: 본문 행 글자 수의 평균에 비례(가장 긴 칸 하나에 끌려가지 않게), 최소 6자·최대 200자로 자름
    weights = []
    for c in range(cols):
        lengths = [len(re.sub(r"\*\*|`", "", r[c])) if c < len(r) else 0 for r in rows[1:]] or [6]
        weights.append(max(6, min(sum(lengths) / len(lengths), 200)))
    total = sum(weights)
    # 최소 폭: 머리글이 한 줄에 들어갈 만큼(9.5pt 한글 한 자 ≈ 190dxa + 칸 여백). 남는 폭을 글자 수 비율로 나눈다.
    # (예전에는 마지막 열이 남는 폭을 떠안아, 다른 열이 최소 폭으로 늘면 마지막 열이 한 글자씩 꺾였다.)
    header = [re.sub(r"\*\*|`", "", rows[0][c]) if c < len(rows[0]) else "" for c in range(cols)]
    mins = [max(1200, min(len(h), 8) * 190 + 260) for h in header]
    spare = max(0, CELL_WIDTH - sum(mins))
    widths = [m + int(spare * w / total) for m, w in zip(mins, weights)]
    widths[widths.index(max(widths))] += CELL_WIDTH - sum(widths)
    grid = "".join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    border = f'w:val="single" w:sz="4" w:space="0" w:color="{BORDER}"'
    xml = [
        f'<w:tbl><w:tblPr><w:tblW w:w="{CELL_WIDTH}" w:type="dxa"/>'
        f'<w:tblBorders><w:top {border}/><w:left {border}/><w:bottom {border}/><w:right {border}/>'
        f'<w:insideH {border}/><w:insideV {border}/></w:tblBorders><w:tblLayout w:type="fixed"/>'
        f'<w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="40" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tblCellMar>'
        f'</w:tblPr><w:tblGrid>{grid}</w:tblGrid>'
    ]
    for r_i, row in enumerate(rows):
        header = r_i == 0
        # 머리 행과 첫 행은 다음 행과 같은 쪽에(앞 제목의 keepNext와 이어져 제목만 쪽 끝에 남지 않게)
        trpr = "<w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>" if header else "<w:trPr><w:cantSplit/></w:trPr>"
        xml.append(f"<w:tr>{trpr}")
        for c in range(cols):
            text = row[c] if c < len(row) else ""
            shade = '<w:shd w:val="clear" w:color="auto" w:fill="EEF2F6"/>' if header else ""
            xml.append(
                f'<w:tc><w:tcPr><w:tcW w:w="{widths[c]}" w:type="dxa"/>{shade}</w:tcPr>'
                f"{para(runs(text, SMALL_SZ + 1, HEAD_COLOR if header else None, bold=header), after=0, keep_next=r_i <= 1)}</w:tc>"
            )
        xml.append("</w:tr>")
    xml.append("</w:tbl>")
    # 표 뒤에는 문단이 있어야 한다(칸 안의 표 규칙)
    return "".join(xml) + para("", after=40)


def section_xml(lines: list[str], media: "Media") -> str:
    xml = []
    items = blocks(lines)
    for n, (kind, content) in enumerate(items):
        # 제목과 표·그림 바로 앞 설명 문단은 다음 내용과 같은 쪽에 둔다(쪽 끝에 제목만 남지 않게).
        before_block = n + 1 < len(items) and items[n + 1][0] in ("table", "image")
        if kind == "image":
            caption, path = content
            xml.append(media.figure(CONTEST / path, caption, int(CELL_WIDTH * 0.8)))  # 그림은 칸 폭의 80%(쪽 수를 줄임, 글자는 그대로 읽힘)
        elif kind == "table":
            xml.append(table(content))
        elif kind == "bullet":
            xml.append(para(runs(content), num=True, after=40))
        elif kind == "num":
            xml.append(para(runs(content), after=40, ind='<w:ind w:left="284" w:hanging="284"/>'))
        elif kind == "src":
            xml.append(para(runs(content, SMALL_SZ - 1, "595959"), after=20, ind='<w:ind w:left="227" w:hanging="227"/>'))
        elif kind == "quote":
            xml.append(para(runs(content, SMALL_SZ, "595959"), after=60))
        else:
            is_heading = content.startswith("**") and content.count("**") == 2 and content.endswith("**")
            if is_heading:
                xml.append(para(runs(content, BODY_SZ + 1, HEAD_COLOR), before=100, after=40, keep_next=True))
            else:
                xml.append(para(runs(content), after=60, keep_next=before_block))
    return "".join(xml) or para("")


# ---------------------------------------------------------------- 그림

def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"not a PNG: {path}")
    return struct.unpack(">II", head[16:24])


def image_run(rid: str, pic_id: int, name: str, w_px: int, h_px: int, width_emu: int) -> str:
    height_emu = int(width_emu * h_px / w_px)
    return (
        f'<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">'
        f'<wp:extent cx="{width_emu}" cy="{height_emu}"/><wp:docPr id="{pic_id}" name="{html.escape(name)}"/>'
        f'<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
        f'<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:nvPicPr><pic:cNvPr id="{pic_id}" name="{html.escape(name)}"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{width_emu}" cy="{height_emu}"/></a:xfrm>'
        f'<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:ln w="6350"><a:solidFill><a:srgbClr val="{BORDER}"/></a:solidFill></a:ln></pic:spPr>'
        f"</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
    )


class Media:
    """문서에 넣는 그림. 같은 파일은 한 번만 넣고 관계 id를 다시 쓴다."""

    def __init__(self, media_dir: Path):
        self.media_dir = media_dir
        self.rels: list[tuple[str, str]] = []
        self._by_path: dict[Path, str] = {}
        self._next_pic = 9000

    def rid(self, src: Path) -> str:
        src = src.resolve()
        if src not in self._by_path:
            n = len(self._by_path) + 1
            target = f"picture_{n}.png"
            shutil.copy(src, self.media_dir / target)
            rid = f"rIdPicture{n}"
            self.rels.append((rid, f"media/{target}"))
            self._by_path[src] = rid
        return self._by_path[src]

    def inline(self, src: Path, width_dxa: int) -> str:
        if not src.exists():
            raise SystemExit(f"그림 파일이 없음: {src}")
        w, h = png_size(src)
        self._next_pic += 1
        return image_run(self.rid(src), self._next_pic, src.name, w, h, int(width_dxa * 635))  # 1 dxa = 635 EMU

    def figure(self, src: Path, caption: str, width_dxa: int) -> str:
        return para(self.inline(src, width_dxa), after=20) + para(runs(caption, SMALL_SZ, "595959"), after=80)


def screens_xml(media: Media) -> str:
    half = CELL_WIDTH // 2
    cells = []
    for file, caption in SCREEN_ORDER:
        src = SCREENS / file
        if not src.exists():
            continue
        pic = para(media.inline(src, half - 160), after=20)
        cap = para(runs(caption, SMALL_SZ, "595959"), after=0)
        cells.append(f'<w:tc><w:tcPr><w:tcW w:w="{half}" w:type="dxa"/></w:tcPr>{pic}{cap}</w:tc>')
    if not cells:
        return ""
    if len(cells) % 2:
        cells.append(f'<w:tc><w:tcPr><w:tcW w:w="{half}" w:type="dxa"/></w:tcPr>{para("")}</w:tc>')
    rows = "".join(f"<w:tr><w:trPr><w:cantSplit/></w:trPr>{cells[i]}{cells[i + 1]}</w:tr>" for i in range(0, len(cells), 2))
    none = 'w:val="nil"'
    return (
        f'<w:tbl><w:tblPr><w:tblW w:w="{CELL_WIDTH}" w:type="dxa"/><w:tblBorders><w:top {none}/><w:left {none}/>'
        f'<w:bottom {none}/><w:right {none}/><w:insideH {none}/><w:insideV {none}/></w:tblBorders><w:tblLayout w:type="fixed"/>'
        f'<w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="40" w:type="dxa"/><w:bottom w:w="100" w:type="dxa"/><w:right w:w="40" w:type="dxa"/></w:tblCellMar>'
        f'</w:tblPr><w:tblGrid><w:gridCol w:w="{half}"/><w:gridCol w:w="{CELL_WIDTH - half}"/></w:tblGrid>{rows}</w:tbl>'
        + para("", after=0)
    )


# ---------------------------------------------------------------- 양식 채우기

def fill(template: Path, draft: Path, out: Path) -> None:
    md = draft.read_text(encoding="utf-8")
    sections = split_sections(md)
    missing = [n for n in range(1, 9) if n not in sections]
    if missing:
        raise SystemExit(f"기획서 초안에 없는 항목: {missing}")
    app_name = re.search(r"앱명 / 프로젝트명: \*\*(.+?)\*\*", md).group(1)

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp) / "doc"
        with zipfile.ZipFile(template) as z:
            for info in z.infolist():
                # 링크·절대 경로·상위 폴더로 나가는 항목은 풀지 않는다(외부 파일).
                name = info.filename
                if name.startswith("/") or ".." in Path(name).parts or (info.external_attr >> 16) & 0o170000 == 0o120000:
                    continue
                z.extract(info, work)
        doc_path = work / "word" / "document.xml"
        doc = doc_path.read_text(encoding="utf-8")

        # 제목 글자는 XML에서 여러 조각으로 나뉘어 있다. 조각째로 남아 있는 "심사 자료"(02장 제목)와
        # 그 뒤 첫 "동의서"(03장 제목) 사이를 02장으로 본다.
        start = doc.index("심사 자료")
        end = doc.index("동의서", start)
        part = doc[start:end]

        # 앱명 줄: "앱명 /" 다음 "프로젝트명: ____" 런들을 이름으로 바꾼다(02장 안의 첫 번째 줄만).
        m = re.search(r"(<w:t xml:space=\"preserve\"> 프로젝트명: )_+(</w:t></w:r>)((?:<w:r [^>]*>|<w:r>)<w:rPr>(?:(?!</w:rPr>).)*</w:rPr><w:t>_+</w:t></w:r>)*", part, re.S)
        if not m:
            raise SystemExit("02장의 앱명 줄을 찾지 못함")
        part = part[: m.start()] + f"{m.group(1)}{html.escape(app_name)}{m.group(2)}" + part[m.end():]

        media_dir = work / "word" / "media"
        media_dir.mkdir(exist_ok=True)
        media = Media(media_dir)

        tables = list(re.finditer(r"<w:tbl>.*?</w:tbl>", part, re.S))
        if len(tables) != 8:
            raise SystemExit(f"02장 표가 8개가 아님: {len(tables)} — 양식이 바뀌었는지 확인")
        new_part = []
        last = 0
        for n, t in enumerate(tables[:8], start=1):
            # 양식 칸은 행을 쪼개지 않게(cantSplit) 되어 있어, 한 쪽보다 긴 내용이 통째로 다음 쪽으로 밀리거나 잘린다.
            # 내용이 쪽을 넘어 이어지게 바깥 행의 설정만 뺀다(칸 안의 작은 표 행은 그대로 둔다).
            tbl = t.group(0).replace("<w:cantSplit/>", "", 1)
            tc_start = tbl.index("</w:tcPr>") + len("</w:tcPr>")
            tc_end = tbl.rindex("</w:tc>")
            content = section_xml(sections[n], media)
            if n == 7:
                content += screens_xml(media)
            new_part.append(part[last:t.start()])
            new_part.append(tbl[:tc_start] + content + tbl[tc_end:])
            last = t.end()
        rest = part[last:]
        if "annex" in sections:
            # 02장 끝 안내 문단(첫 문단) 뒤, 03장 제목(쪽 넘김) 앞에 새 쪽으로 별지를 넣는다.
            cut = rest.index("</w:p>") + len("</w:p>")
            annex = '<w:p><w:r><w:br w:type="page"/></w:r></w:p>' + section_xml(sections["annex"], media)
            rest = rest[:cut] + annex + rest[cut:]
        new_part.append(rest)
        doc = doc[:start] + "".join(new_part) + doc[end:]
        # 양식의 아래 여백(907)이 바닥글 위치(720) + 바닥글 한 줄보다 작아, 칸이 쪽 끝까지 차면 칸 테두리가 바닥글 글자를 긋는다.
        # 바닥글 한 줄(약 10pt)과 여유가 들어가게 아래 여백만 늘린다(쪽 크기·나머지 여백은 그대로).
        def clear_footer(m: re.Match) -> str:
            bottom, footer = int(m.group(2)), int(m.group(4))
            return m.group(1) + str(max(bottom, footer + FOOTER_CLEARANCE)) + m.group(3) + m.group(4) + m.group(5)
        doc = re.sub(r'(<w:pgMar[^>]*w:bottom=")(\d+)("[^>]*w:footer=")(\d+)(")', clear_footer, doc)
        doc_path.write_text(doc, encoding="utf-8")

        # 그림 관계
        rels_path = work / "word" / "_rels" / "document.xml.rels"
        rel_xml = rels_path.read_text(encoding="utf-8")
        img_type = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
        add = "".join(f'<Relationship Id="{rid}" Type="{img_type}" Target="{target}"/>' for rid, target in media.rels)
        rels_path.write_text(rel_xml.replace("</Relationships>", add + "</Relationships>"), encoding="utf-8")

        # 문서 정보: 양식에서 따라온 사람 이름(마지막 수정자)을 지운다. 제출 자료에 실명이 남지 않게.
        core_path = work / "docProps" / "core.xml"
        if core_path.exists():
            core = core_path.read_text(encoding="utf-8")
            core = re.sub(r"<cp:lastModifiedBy>[^<]*</cp:lastModifiedBy>", "<cp:lastModifiedBy></cp:lastModifiedBy>", core)
            core = re.sub(r"<dc:creator>[^<]*</dc:creator>", "<dc:creator></dc:creator>", core)
            core_path.write_text(core, encoding="utf-8")

        # PNG 형식 선언
        ct_path = work / "[Content_Types].xml"
        ct = ct_path.read_text(encoding="utf-8")
        if 'Extension="png"' not in ct:
            ct = ct.replace('<Default Extension="xml"', '<Default Extension="png" ContentType="image/png"/><Default Extension="xml"', 1)
            ct_path.write_text(ct, encoding="utf-8")

        # 글머리표 번호 정의(양식에 없는 번호를 새로 추가)
        num_path = work / "word" / "numbering.xml"
        num = num_path.read_text(encoding="utf-8")
        if f'w:abstractNumId="{BULLET_NUM_ID}"' not in num:
            abstract = (
                f'<w:abstractNum w:abstractNumId="{BULLET_NUM_ID}"><w:multiLevelType w:val="singleLevel"/>'
                f'<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/>'
                f'<w:pPr><w:ind w:left="284" w:hanging="227"/></w:pPr></w:lvl></w:abstractNum>'
            )
            first_num = num.index("<w:num ")
            num = num[:first_num] + abstract + num[first_num:]
            num = num.replace("</w:numbering>", f'<w:num w:numId="{BULLET_NUM_ID}"><w:abstractNumId w:val="{BULLET_NUM_ID}"/></w:num></w:numbering>')
            num_path.write_text(num, encoding="utf-8")

        out.parent.mkdir(parents=True, exist_ok=True)
        tmp_out = out.with_suffix(".tmp.docx")
        with zipfile.ZipFile(tmp_out, "w", zipfile.ZIP_DEFLATED) as z:
            # [Content_Types].xml을 맨 앞에 둔다(일부 프로그램이 순서를 본다).
            z.write(ct_path, "[Content_Types].xml")
            for path in sorted(work.rglob("*")):
                rel = path.relative_to(work).as_posix()
                if path.is_file() and rel != "[Content_Types].xml":
                    z.write(path, rel)
        tmp_out.replace(out)
    print(f"만듦: {out.relative_to(ROOT)} (그림 파일 {len(media.rels)}개)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--template", type=Path, default=TEMPLATE)
    parser.add_argument("--draft", type=Path, default=DRAFT)
    parser.add_argument("--out", type=Path, default=OUT)
    args = parser.parse_args()
    fill(args.template, args.draft, args.out)


if __name__ == "__main__":
    main()
