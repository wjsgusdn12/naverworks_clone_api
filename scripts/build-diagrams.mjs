// README.md 안의 Mermaid 블록을 SVG로 뽑아 docs/diagrams/ 에 저장하고,
// 앱의 "프로젝트 소개" 모달이 읽을 docs/guide.json 을 함께 만든다.
// README가 단일 원본이라 그림을 고칠 때는 README만 고치고 이 스크립트를 다시 돌리면 된다.
//
// 사용법:  node scripts/build-diagrams.mjs
// 블록 표시:  <!-- diagram: 이름 --> 바로 아래 줄에 ```mermaid 블록
// 제목은 가장 가까운 위쪽 "## 제목", 설명은 블록 바로 위·아래 문단을 쓴다.

import { execSync } from "node:child_process";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const lines = readFileSync(join(root, "README.md"), "utf8").split(/\r?\n/);
const outDir = join(root, "docs", "diagrams");

// 문단 경계: 빈 줄, 제목, 구분선, 주석 표시.
const isBoundary = (line) =>
  line.trim() === "" || line.startsWith("#") || line.startsWith("---") || line.startsWith("<!--");

// 표시 위쪽의 가장 가까운 문단.
const paragraphBefore = (from) => {
  let i = from;
  while (i >= 0 && lines[i].trim() === "") i--;
  const buf = [];
  while (i >= 0 && !isBoundary(lines[i])) buf.unshift(lines[i--]);
  return buf.join(" ").trim();
};

// 블록 아래의 첫 문단.
const paragraphAfter = (from) => {
  let i = from;
  while (i < lines.length && lines[i].trim() === "") i++;
  const buf = [];
  while (i < lines.length && !isBoundary(lines[i])) buf.push(lines[i++]);
  return buf.join(" ").trim();
};

const sections = [];
let heading = "";
for (let i = 0; i < lines.length; i++) {
  const headingMatch = lines[i].match(/^## (.+)$/);
  if (headingMatch) heading = headingMatch[1].trim();
  const marker = lines[i].match(/^<!-- diagram: ([a-z0-9-]+) -->$/);
  if (!marker) continue;
  if (lines[i + 1] !== "```mermaid") {
    throw new Error(`${marker[1]}: 표시 다음 줄이 \`\`\`mermaid 가 아님`);
  }
  const end = lines.indexOf("```", i + 2);
  sections.push({
    id: marker[1],
    title: heading,
    description: [paragraphBefore(i - 1), paragraphAfter(end + 1)].filter(Boolean).join("\n"),
    code: lines.slice(i + 2, end).join("\n"),
  });
}

rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

// mermaid-cli 설정: 한글 폰트, 흰 배경.
const configPath = join(outDir, "mermaid.config.json");
writeFileSync(
  configPath,
  JSON.stringify({
    theme: "default",
    themeVariables: {
      fontFamily: "Pretendard, Malgun Gothic, Apple SD Gothic Neo, sans-serif",
    },
  }),
);

const guide = [];
for (const section of sections) {
  const mmd = join(outDir, `${section.id}.mmd`);
  const svg = join(outDir, `${section.id}.svg`);
  writeFileSync(mmd, section.code + "\n");
  // puppeteer 브라우저로 렌더링. 최초 1회 Chromium을 내려받는다.
  execSync(
    `npx -y -p @mermaid-js/mermaid-cli mmdc -i "${mmd}" -o "${svg}" -c "${configPath}" -b white -q`,
    { stdio: "inherit" },
  );
  guide.push({
    id: section.id,
    title: section.title,
    description: section.description,
    svg: `docs/diagrams/${section.id}.svg`,
  });
  console.log(`✓ ${section.id}`);
}
rmSync(configPath);
writeFileSync(join(root, "docs", "guide.json"), JSON.stringify(guide, null, 2) + "\n");
console.log(`완료: ${guide.length}개 → docs/diagrams, docs/guide.json`);
