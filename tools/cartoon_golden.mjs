// 카툰 시안(docs/cartoon/카툰_상세안.html)의 규칙으로 대조용 값을 만든다.
// 앱의 CartoonWriter가 시안과 같은 만화를 쓰는지 CartoonGoldenTest가 이 값과 비교한다.
//
// 사용법(저장소 루트에서): node tools/cartoon_golden.mjs
// 결과: app/chatoon/src/test/resources/cartoon/golden.jsonl
// 시안의 규칙이나 문장을 바꾸면 다시 실행하고, 앱 코드도 같이 고친다.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const html = readFileSync(join(root, 'docs/cartoon/카툰_상세안.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

// 시안 스크립트는 화면을 그리며 시작한다. 규칙만 쓰려고 화면은 빈 것으로 대신한다.
const elements = {};
const element = (id) => elements[id] ?? (elements[id] = {
  id, textContent: '', innerHTML: '', hidden: false, value: '',
  addEventListener() {}, getAttribute() { return null; }, parentNode: { id: '' },
});
const initial = { time: 'dusk', wiper: '2', park: '3', windows: 'up', temp: '22', guests: '0', stranger: 'no', why: '', jolt: 'none', why2: '' };
globalThis.document = { getElementById: element };
globalThis.window = {};
globalThis.FormData = function FormData() { this.get = (key) => initial[key]; };
new Function(script)();
const { build } = globalThis.window.__cartoon;

// 컷 하나를 한 줄로. 앱 쪽 테스트도 같은 순서로 적는다.
const cutLine = (c) => [
  c.kind, c.mood ?? '', c.driver === 'stranger' ? 'stranger' : '', c.fx || '',
  c.kind === 'park' ? c.n : '', c.kind === 'temp' ? c.t : '',
  c.windows === 'down' ? 'down' : '', c.guests || '', c.wind ? 'wind' : '',
  c.q ? c.q.join('/') : '', c.why ?? '', c.cap, c.proof ?? '',
].join('|');

const episodeText = (ep) => [
  ep.title, ep.line, ep.ask ? ep.ask.join('/') : '-', ep.said || '-', cutLine(ep.now), ...ep.cuts.map(cutLine),
].join('\n');

const cases = [];
const add = (state) => {
  const st = { guests: 0, stranger: false, ...state };
  if (st.jolt === 'none') st.why2 = '';
  cases.push({ in: st, out: episodeText(build(st)) });
};

const answers = [['', ''], ['면접 보러', ''], ['엄마 병원', '한눈팔다가 사고 날 뻔']];
for (const time of ['day', 'dusk', 'night'])
  for (const wiper of [0, 2])
    for (const park of [1, 3, 5, 7])
      for (const windows of [false, true])
        for (const temp of [17, 22])
          for (const jolt of ['none', 'brake', 'accel'])
            for (const [why, why2] of answers)
              add({ time, wiper, park, windows, temp, jolt, why, why2 });

// 격자에 없는 값들: 보통 비, 히터, 손님, 낯선 운전자, 사전의 여러 상황, 사전에 없는 말
const base = { time: 'day', wiper: 0, park: 5, windows: false, temp: 22, jolt: 'none', why: '', why2: '' };
add({ ...base, wiper: 1 });
add({ ...base, wiper: 1, windows: true });
add({ ...base, temp: 27 });
add({ ...base, temp: 26, time: 'night' });
add({ ...base, temp: 18 });
add({ ...base, guests: 1 });
add({ ...base, guests: 3, park: 2 });
add({ ...base, guests: 2, why: '소개팅' });
add({ ...base, stranger: true });
add({ ...base, stranger: true, park: 7 });
add({ ...base, stranger: true, why: '회사 야근', time: 'night' });
for (const why of ['소개팅', '데리러 감', '회사 야근', '코스트코', '헬스', '점심 먹으러', '바다 보러', '그냥 답답해서', '달 보러 감', '시험', '할머니 댁'])
  add({ ...base, why, time: 'night', windows: true });
for (const why2 of ['앞차가 너무 느려서', '갑자기 끼어들어서', '고양이가 튀어나와서', '노란불이라서', '늦어서', '사고 날 뻔', '모르겠어요'])
  for (const jolt of ['brake', 'accel'])
    add({ ...base, jolt, why2, time: 'dusk' });

const out = join(root, 'app/chatoon/src/test/resources/cartoon/golden.jsonl');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, cases.map((c) => JSON.stringify(c)).join('\n') + '\n');
console.log(`${cases.length}건 → ${out}`);
