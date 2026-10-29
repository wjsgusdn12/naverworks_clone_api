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

// 모달용 SVG에만 입히는 밝은 테마. README 블록에는 색을 넣지 않아 GitHub이 밝음·어둠 모드에 맞춰 알아서 그린다.
// (README 블록의 frontmatter에는 줄바꿈 같은 배치 설정만 둔다.)
const themeVariables = `    fontSize: 14px
    primaryColor: "#eef2ff"
    primaryTextColor: "#1e1b4b"
    primaryBorderColor: "#818cf8"
    secondaryColor: "#f0f9ff"
    secondaryBorderColor: "#7dd3fc"
    tertiaryColor: "#f8fafc"
    tertiaryBorderColor: "#cbd5e1"
    lineColor: "#64748b"
    textColor: "#1e293b"
    clusterBkg: "#f8fafc"
    clusterBorder: "#cbd5e1"
    edgeLabelBackground: "#ffffff"
    nodeBorder: "#818cf8"
    mainBkg: "#eef2ff"
    actorBkg: "#eef2ff"
    actorBorder: "#818cf8"
    actorTextColor: "#1e1b4b"
    actorLineColor: "#cbd5e1"
    signalColor: "#334155"
    signalTextColor: "#334155"
    labelBoxBkgColor: "#f1f5f9"
    labelBoxBorderColor: "#cbd5e1"
    noteBkgColor: "#fef3c7"
    noteBorderColor: "#fbbf24"
    noteTextColor: "#78350f"
    activationBkgColor: "#e0e7ff"
    activationBorderColor: "#818cf8"
    cScale0: "#312e81"
    cScale1: "#4338ca"
    cScale2: "#4f46e5"
    cScale3: "#6366f1"
    cScale4: "#818cf8"
    cScaleLabel0: "#ffffff"
    cScaleLabel1: "#ffffff"
    cScaleLabel2: "#ffffff"
    cScaleLabel3: "#ffffff"
    cScaleLabel4: "#ffffff"
    cScalePeer0: "#312e81"
    cScalePeer1: "#4338ca"
    cScalePeer2: "#4f46e5"
    cScalePeer3: "#6366f1"
    cScalePeer4: "#818cf8"
    cScaleInv0: "#312e81"
    cScaleInv1: "#4338ca"
    cScaleInv2: "#4f46e5"
    cScaleInv3: "#6366f1"
    cScaleInv4: "#818cf8"
    attributeBackgroundColorEven: "#f8fafc"
    attributeBackgroundColorOdd: "#ffffff"`;

// 흐름도의 저장소(원기둥)·외부 서비스·중심(원) 노드를 색으로 구분.
const classDefs = `    classDef store fill:#ecfdf5,stroke:#34d399,color:#064e3b
    classDef external fill:#fff7ed,stroke:#fb923c,color:#7c2d12
    classDef focus fill:#4f46e5,stroke:#4338ca,color:#ffffff,font-weight:bold`;

// README 블록의 frontmatter(배치 설정)에 테마를 합치고, 흐름도면 노드 클래스를 붙인다.
const styled = (code) => {
  const match = code.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  const layout = match ? match[1].replace(/^config:\n/, "") : "";
  let body = match ? match[2] : code;
  const kind = body.trim().split(/\s/)[0];
  if (kind === "flowchart") {
    const ids = (pattern) => [...new Set([...body.matchAll(pattern)].map((m) => m[1]))];
    const stores = ids(/^\s*(\w+)\[\(/gm);
    const externals = ids(
      /^\s*(\w+)\["[^"]*(?:외부|LLM|Vercel|GitHub|DuckDNS|Supabase)[^"]*"\]/gm,
    ).filter((id) => !stores.includes(id));
    const focus = ids(/^\s*(\w+)\(\(/gm);
    body = body.trimEnd() + "\n" + classDefs;
    if (stores.length) body += `\n    class ${stores.join(",")} store`;
    if (externals.length) body += `\n    class ${externals.join(",")} external`;
    if (focus.length) body += `\n    class ${focus.join(",")} focus`;
  }
  return `---
config:
  theme: base
  fontFamily: "Pretendard, Malgun Gothic, Apple SD Gothic Neo, Noto Sans KR, sans-serif"
${layout ? layout + "\n" : ""}  themeVariables:
${themeVariables}
---
${body.trimEnd()}
`;
};

const guide = [];
for (const section of sections) {
  const mmd = join(outDir, `${section.id}.mmd`);
  const svg = join(outDir, `${section.id}.svg`);
  writeFileSync(mmd, styled(section.code));
  // puppeteer 브라우저로 렌더링. 최초 1회 Chromium을 내려받는다.
  execSync(`npx -y -p @mermaid-js/mermaid-cli mmdc -i "${mmd}" -o "${svg}" -b white -q`, {
    stdio: "inherit",
  });
  guide.push({
    id: section.id,
    title: section.title,
    description: section.description,
    svg: `docs/diagrams/${section.id}.svg`,
  });
  console.log(`✓ ${section.id}`);
}
writeFileSync(join(root, "docs", "guide.json"), JSON.stringify(guide, null, 2) + "\n");
console.log(`완료: ${guide.length}개 → docs/diagrams, docs/guide.json`);
