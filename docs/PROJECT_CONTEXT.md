# Enterprise Collaboration Platform — 코드 에이전트 완전 인수인계서

> 갱신: 2026-10-20  
> 프로젝트: 네이버웍스의 **협업 기능을 참고**한 Java/Spring 기업용 실시간 협업 플랫폼  
> 문서 성격: **구현 완료 목록이 아니라 전체 제품 요구사항·학습 목표·기술 선택·개발 계획의 기준 문서**  
> 우선순위 표기: **[핵심] [2차] [후순위] [실험] [미확정] [제외] [현재]**  
> **이 문서에 적힌 목표 기술을 이미 설치·구현한 것으로 간주하지 말 것. 실제 소스 코드 확인 필수.**

## 0. 이 문서를 사용하는 법

1. 두 저장소의 `AGENTS.md` → 이 문서 → `docs/PROGRESS.md` → 실제 소스/빌드 설정/README 순으로 확인한다.
2. **[현재]**는 사용자에게 확인받은 현황이다. 코드 확인 전에는 '사용자 보고'로 취급한다.
3. **[핵심]**은 구현하기로 한 주요 범위, **[2차]**는 핵심이 돌아간 다음 추가할 기능, **[후순위]**는 시간이 허용되면 진행, **[실험]**은 문제 재현/대안 비교를 위한 후보이지 무조건 운영 코드에 넣는 기술이 아니다.
4. **[미확정]**을 개발자가 임의 확정하지 않는다. 결정이 필요한 시점에 대안을 간단히 비교해 승인받는다.
5. 범위는 크지만 **전부 동시에 구현하지 않는다.** 한 단계의 작동·테스트·문서화를 끝내고 다음으로 간다.
6. 본 프로젝트는 구직용 개인 프로젝트다. 측정하지 않은 수치나 발생하지 않은 장애를 사실로 꾸미지 않는다.

## 1. 프로젝트 목적과 정체성

**한 줄 소개**

> 실시간 메신저와 일정·회의실 예약, 조직 기반 권한 관리를 중심으로 만들고, WebSocket·Redis·JPA·동시성·조회 성능·Scale-out·관측 가능성·외부 AI 연동을 직접 실험·측정·개선하는 기업용 협업 플랫폼.

- 네이버웍스의 서비스 개념 및 일부 UX를 참고하지만 **네이버웍스의 비공개 내부 아키텍처를 재현한다는 뜻은 아니다.**
- 메신저가 메인이고, 조직/인증/권한 및 캘린더/회의실 예약이 나머지 2개 핵심 도메인이다.
- 프론트 UI를 완성하는 것도 중요하지만 주력 평가 항목은 **백엔드 설계의 깊이, 테스트, 장애/성능 실험과 판단 근거**다.
- 스토리텔링 원칙: **기능 → 문제 재현 → 원인 분석 → 대안/트레이드오프 → 구현 → 테스트/측정 → 개선 → 한계 기록**.
- **프로젝트/태스크(Jira·Kanban·프로젝트 관리)는 제외**하기로 확정했다.
- 1~3년차 Java/Spring 이직용 **메인 Deep Dive 프로젝트 한 개**가 목적이다. 작은 토이 프로젝트를 반드시 여러 개 만들 필요 없음.
- AI 모델 직접 학습, RAG, Agent, MSA, Kafka, Kubernetes를 초기부터 끼워 넣지 않는다.

## 2. VS Code / Git 저장소

```text
JHWJHW/                               # 작업 공간 상위 폴더, Git 저장소 만들지 않음
├── naverworks_clone_api/             # 백엔드 GitHub 저장소 (독립)
│   ├── AGENTS.md
│   ├── README.md                     # 기존 파일이 있다면 그대로 보존
│   └── docs/
│       ├── PROJECT_CONTEXT.md       # 이 문서. 제품·개발 기준 단일 원본
│       ├── PROGRESS.md              # 실제 구현 현황
│       ├── FEATURE_CHECKLIST.md      # 기능별 점검표
│       └── ENGINEERING_EXPERIMENTS.md# 성능·장애·트러블슈팅 실험 기준
└── naverworks_clone_front/           # Next.js GitHub 저장소 (독립)
    ├── AGENTS.md
    └── README.md                     # 기존 파일 보존
```

- VS Code **상위 폴더 하나로 열 수 있고**, 각 하위 프로젝트에서 Commit/Push를 **각각 별도로** 한다.
- 별도 Git remote, 브랜치, 이력 유지. 프로젝트별로 하나의 Git 저장소를 두며 `JHWJHW/.git`을 만들지 않는다.
- 프론트 에이전트는 `../naverworks_clone_api/docs/PROJECT_CONTEXT.md`를 함께 읽는다. 접근 불가하면 요청한다.
- 사용자가 요청하지 않으면 Git 커밋/푸시, force-push, 원격 변경, 브랜치 교체, 대량 삭제를 하지 않는다.

## 3. 실제 개발 현황 (2026-10-20 사용자 보고)

### [현재] 프론트

- **로그인 화면 UI 구현됨.**
- **홈 화면 UI 구현됨.**
- 프론트에서 `admin / 1234`를 관리자처럼 **임시로 판단해 로그인 및 홈 이동**시키는 목업 로직이 존재한다고 함.
- 로그인 화면에 **네이버 / 카카오 / 구글 소셜 로그인 버튼의 UI만** 만들어 놓음.
- 현재 ID/PW 검증은 **Spring Boot 서버·DB·Spring Security를 통한 실제 인증이 아님**.
- 소셜 로그인 OAuth2 흐름, 백엔드 콜백/계정 연동은 **미구현**.
- 이미 만든 화면은 보존하고 실제 API에 연결할 것. UI를 임의로 갈아엎지 말 것.
- 현재 소스, 디자인 컴포넌트, Next 버전, 패키지, 상태관리/라우터는 **에이전트가 직접 확인**해야 함.

### [현재] 백엔드 / DB / 배포

- **프론트-백엔드 API 미연동, DB 미연동, 실제 로그인/인증 API 미구현**.
- 백엔드 프로젝트가 어떤 단계로 세팅되었는지(Spring Boot 버전, Gradle/Maven, 패키지, 테스트) **코드로 확인해야 함**.
- Redis, WebSocket, 실제 채팅, 예약, 대시보드 집계, AI, 모니터링, Scale-out을 완료했다고 가정하면 안 됨.
- GitHub에 API 저장소와 Front 저장소 두 개는 만들어 연동했고, 하나의 VS Code에서 열어둠.

### 임시 인증에 대한 명확한 처리

- `admin / 1234`는 **테스트 UI의 하드코딩된 임시 값일 뿐**. 공개·운영 서비스 비밀번호로 쓰지 않는다.
- 실제 인증을 도입하면 프론트의 임시 분기를 제거한다.
- 실제 개발자/데모 계정은 서버 측 인증, 안전한 비밀번호 해시, 시드 데이터/환경변수로 관리한다. 인증 정보/시크릿은 Git에 넣지 않는다.
- 데모 관리자 전환 기능은 **누구나 자기 실계정을 ROLE_ADMIN으로 승격하는 허점**이 되어서는 안 된다.

## 4. 기능 총괄 / 중요도

| 묶음 | 주요 기능 | 우선순위 |
|---|---|---|
| 계정/조직 | 가입·로그인·로그아웃·내 정보, 회사·부서·구성원·프로필·조직도·검색 | [핵심] |
| 인증/권한 | Spring Security, USER/ADMIN, 조직/리소스별 접근 제한 | [핵심] |
| 데모 모드 | 상단 일반/관리자 모드 버튼 → 확인 모달 → 서버의 실제 인증/인가 전환 → 화면 전환 | [핵심] |
| 채팅방 | 1:1/그룹, 생성·참여·나가기·목록·정렬, 개인별 채팅방 Pin | [핵심]/[2차] |
| 메시지 기본 | 송수신/저장/조회, 그룹 읽음/안읽음, 안읽은 수, Cursor Pagination | [핵심] |
| 메시지 확장 | 답장·수정·삭제·전달·복사·Reaction·공지·파일 첨부 | [2차] |
| 구성원 즐겨찾기 | 개인별 멤버 Favorite | [2차] |
| 실시간/상태 | WebSocket/STOMP, Redis Presence TTL/Heartbeat | [핵심] |
| 서버 확장 | Spring x2 + Nginx + Redis Pub/Sub | [실험→고도화] |
| 일정/예약 | 개인/조직 일정, 오늘/주/월, 회의실·중복 예약/동시성 제어 | [핵심] |
| 일반 홈 | 오늘 일정, 읽지 않은 메시지, 즐겨찾기, 예약 현황 | [핵심] |
| 관리자 통계 | 활성/방문/온라인/신규, 메시지/회의실, 연·월·일·시간 그래프 | [핵심/2차] |
| 운영 지표 | API, JVM, DB Pool, Redis, WS, 외부 LLM + Grafana | [고도화] |
| AI | LLM 기반 채팅방 대화 요약 + 외부 연동 안정성 설계 | [후반] |
| 소셜 로그인 | 네이버·카카오·구글 OAuth2 (현재 버튼 UI만) | [후순위/구현 시점 협의] |
| 알림 | 메시지/언급/예약 등 앱 내 알림 및 읽음/안읽음 | [후순위] |
| 감사 로그 | 관리자 조작/권한 변경 등 감사 추적 | [후순위] |
| 파일 저장/전달 | 메시지 첨부, 파일 접근 제어, 저장소 선택 | [2차/미확정] |
| 자동화 | CI/CD, 공개 데모 배포, 모니터링 연동 | [고도화] |
| 초기 제외 | Project, Task, 무리한 MSA/Kafka/K8s/RAG/Agent/Elasticsearch | [제외] |

## 5. 기능 상세: 계정·조직·권한

### 5.1 [핵심] 계정과 조직

- 회원가입, ID/PW 로그인, 로그아웃, 인증 상태 유지, 내 프로필/회원정보.
- 회사(Company) 생성/가입 또는 데모 회사 사용, 부서(Department), 구성원(Member/User).
- 회사 → 부서 → 구성원 **계층형 조직도**, 조직원 목록/검색.
- 관리자의 부서·구성원·조직 관리, 적절한 사용자 권한 관리.
- 회사별 데이터 격리(다른 회사의 멤버/채팅/예약/통계에 접근 불가), API와 WebSocket 모두 리소스 인가 필수.
- `User`, `Company`, `Department`, `Membership`, `Role` 등 **후보 개념**은 있으나 최종 ERD는 요구사항에 맞춰 확정.

### 5.2 [핵심] 인증/인가

- Spring Security로 실제 인증(Authentication)과 인가(Authorization)를 구현한다.
- `ROLE_USER`와 `ROLE_ADMIN` **최초 역할 구분**. 부서 관리자, 채팅방 OWNER/ADMIN/MEMBER와 별도인 세부 권한은 필요해질 때 설계한다.
- 일반 사용자는 일반 서비스 접근, 관리자는 허용된 조직·사용자 관리 및 비즈니스 통계 접근.
- 일반 사용자 → 관리자 API **403**, 인증 자체가 없으면 **401 또는 로그인 리다이렉트(선택한 인증 방식에 따름)**.
- 프론트 메뉴 숨김은 UX일 뿐 인가의 근거가 아니다. 서버 컨트롤러/서비스/메시지 구독에 정책이 있어야 한다.
- CSRF/CORS, 세션/쿠키/JWT 보안, 비밀번호 해시, 검증/예외, 로그아웃, 만료 등 검토.
- **세션 기반 인증 vs JWT는 미확정**. 소셜 로그인·데모 체험·브라우저 사용 방식까지 비교한 후 결정.

### 5.3 [핵심] 공개 포트폴리오용 USER ↔ ADMIN 데모 모드

- 상단바에 **[일반 모드] [관리자 모드]** 전환 버튼을 노출.
- 다른 모드를 클릭하면 **“관리자 모드로 전환하시겠습니까?” / “일반 모드로 전환하시겠습니까?”** 확인 모달 표시.
- **[취소]** 시 아무 변경 없음, **[전환]** 시 서버 측 데모 계정/세션도 전환 → 사용자 정보 재조회 → UI/홈 구성·API 접근 갱신.
- USER: Today 일정/메시지/즐겨찾기/예약, 일반 메뉴. ADMIN: 활동 통계/조직 관리 등.
- USER에서 관리자 API 직접 호출 → **403**; ADMIN 권한으로 호출 → 허용 시 **200**.
- **권장 구현:** 사전 준비된 데모 USER/ADMIN **별도 계정/세션 전환**. 임의 사용자 권한 상승 API를 공개하지 않는다.
- 공개 체험 관리자는 실제 사용자/공유 데이터 파괴를 막도록 **샌드박스·접근 범위 제한·데이터 재설정**을 검토.
- 상단 UI에는 현재 모드가 분명하게 보이게 표시하며 전환 성공 이후에만 화면 변경.

### 5.4 [후순위] OAuth2 소셜 로그인

- 이미 존재하는 **네이버·카카오·구글 버튼 UI 유지**.
- ID/PW 기반 백엔드 인증을 먼저 완성한 다음 OAuth2 실연동 및 사용자 계정 연결 여부 검토.
- Callback, 토큰 검증, 사용자 식별, 계정 연결/중복 계정, 탈퇴 및 오류 처리 등 설계.
- 소셜 로그인 사용자를 자동으로 ADMIN 승격하지 않는다.
- 구현 전까지 UI는 **“준비 중”으로 안내하거나 오해 없는 비활성화**.

## 6. 기능 상세: 메신저

### 6.1 [핵심] 채팅방

- 1:1 및 그룹 채팅, 방 생성·참여자 관리·방 나가기.
- 목록과 참여 인원/프로필, 최신 메시지, 안 읽은 개수, 최신 활동순 정렬.
- **[2차] 사용자별 Pin 고정:** A가 Pin한 방은 A의 목록에서만 상단 고정, B의 목록에는 영향 없음. 예: `chat_room_pin(user_id, chat_room_id, pinned_at)` + 중복 방지 유니크 제약.
- 채팅방 접근, 목록, 상세, STOMP 구독, 메시지 송신 모두 서버에서 참가자 여부 확인.
- 채팅방 관리자/방장 권한은 메시지 공지 등 실제 기능이 필요할 때 확장.

### 6.2 [핵심] 메시지 기본

- 메시지 생성/전송/DB 저장/수신/목록/과거 기록 조회.
- 실시간 1:1·그룹 채팅 및 **그룹 읽음/안읽음**과 사용자별 안 읽은 수.
- 읽음 모델 후보: 메시지별 읽음 레코드 vs 사용자별 마지막 읽은 메시지 커서(`last_read_message_id`), 실제 요구·성능 검토 후 결정.
- 메시지는 RDBMS를 저장 원천으로 사용한다.
- **Cursor Pagination**: 예 `WHERE room_id = ? AND id < ? ORDER BY id DESC LIMIT 50`, `(room_id,id)` 복합 인덱스 검토.
- 중복 전송/재연결/연결 단절·순서 보장 수준을 검토. Redis Pub/Sub을 DB 영속 저장 대체재로 쓰지 않는다.

### 6.3 [2차] 메시지 UX 세부 기능 (전부 포함)

- **답장(Reply)**: 특정 원본 메시지를 참조해 답장.
- **수정(Edit)**: 작성자 등 허가된 주체만 수정, 수정 여부/시간 검토.
- **삭제(Delete)**: 작성자 권한, 소프트 삭제·첨부 및 답장/공지 영향 정책 검토.
- **전달(Forward)**: 다른 채팅방으로 전달, **전달 시점 내용 스냅샷 보존을 우선 검토**. 원본 수정/삭제와 전달본 관계·권한·첨부 정책 검토.
- **복사(Copy)**: 원문 텍스트를 브라우저 Clipboard로 복사. 보통 별도 백엔드 API 불필요.
- **이모지 반응(Reaction)**: 👍❤️😂 등 추가·취소, 이모지별 카운트, 참여 사용자 목록(필요할 때), 실시간 업데이트; 예 `UNIQUE(message_id,user_id,reaction_type)`.
- **메시지 공지(Notice)**: 특정 메시지를 채팅방 공지로 등록·해제; 등록 권한, 공지 개수, 삭제/수정 시 정책 정의.
- **파일 첨부(Attachment)**: 파일 업로드/다운로드, 메타데이터 저장, 접근 권한, 크기·확장자·악성파일 방어, 만료/삭제; **S3/Supabase Storage 등 저장소 미확정**.
- **메시지 검색**은 QueryDSL 복합 조회 용도로 논의된 **후순위 기능**; 필요하면 진행.
- 모든 메시지 상태 변화에서 이벤트의 실시간 반영, 권한, 중복 처리 고려.

### 6.4 [2차] 멤버 즐겨찾기

- 다른 조직원을 개인 즐겨찾기에 추가/제거, 즐겨찾기 목록/정렬.
- 예: `member_favorite(user_id,target_user_id,created_at)`, `UNIQUE(user_id,target_user_id)`.
- 조직이 다른 사용자를 즐겨찾기에 추가할 수 있는지 정책 정의(기본적으로 조직별 접근 제한 고려).

### 6.5 [후순위] 알림

- 메신저 메시지/언급, 공지, 예약 등에서 **앱 내 알림**을 제공하는 방향으로 논의.
- 알림 목록, 읽음/안읽음, 실시간 알림 전달, 중복 알림/사용자 권한 검증.
- Redis Pub/Sub은 즉시 전달용. 알림을 유실 없이 남겨야 하면 **DB에 알림 원본 저장** 등 별도 보장 설계.
- Kafka를 알림 때문에 **초기부터 무조건 도입하지 않는다**.

## 7. 기능 상세: WebSocket · Presence · Scale-out

### 7.1 [핵심] 단일 서버 WebSocket/STOMP

- 초기에 Spring Boot **단일 인스턴스**로 채팅과 STOMP 구독/발행 구현.
- REST API: 방/메시지 기록/설정 등, WebSocket/STOMP: 실시간 메시지/반응/읽음/상태 이벤트.
- 방별 destination 예시는 구현 시 결정, 클라이언트가 임의 방을 구독/발행하지 못하도록 실제 서버 인가.
- 연결 종료, 재연결, 중복 이벤트, 읽음 상태 불일치 및 다중 탭 등 처리.

### 7.2 [핵심] Redis TTL/Heartbeat Presence

- 사용자 온라인 여부를 단일 서버 `ConcurrentHashMap`만으로 판단하지 않음.
- 접속 후 Redis에 연결별 또는 사용자별 Presence 키를 TTL과 함께 유지, Heartbeat로 TTL 갱신.
- **20초 Heartbeat / 60초 TTL은 예시이지 확정 값 아님**.
- 비정상 종료/네트워크 단절 시 Heartbeat 중단 → TTL 만료 → 오래된 ONLINE 자동 정리.
- 정상 Disconnect에서는 가능한 즉시 정리하되 **여러 탭/기기** 연결 중 하나만 닫혀도 사용자를 OFFLINE으로 만들지 않음.
- 구세션 늦은 Disconnect가 새 연결 상태를 지우는 경쟁조건, 스캔 비용·Redis 명령 수 검토.
- Presence는 TTL 때문에 **약간의 감지 지연이 있는 근사적 상태**이며 즉시 정확성 보장이라고 표현하지 않는다.

### 7.3 [실험/후반 핵심] Scale-out

- **하나의 백엔드 코드 저장소**, 동일 코드로 Spring Boot 인스턴스 #1/#2 실행. **소스 저장소를 두 개로 복제하지 않음**.
- Nginx 리버스 프록시/로드밸런싱 및 WebSocket upgrade/장기 연결 처리.
- A는 Server #1, B는 Server #2에 연결된 재현 환경에서 **서버 로컬 메모리만으로 이벤트가 전달되지 않는 한계** 확인.
- Redis Pub/Sub으로 서버 간 이벤트 전파, 서버마다 자기 연결 사용자에게 전송.
- 메시지 DB 저장 순서와 이벤트 발행 시점, 실패/중복/재전달, 여러 서버 사이 중복 브로드캐스팅 방지 검토.
- Redis Pub/Sub에는 메시지 **영속성·재처리·오프라인 수신 보장 없음**. 필요한 메시지는 DB에서 다시 조회/동기화.
- **Server #1/#2 = 앱 인스턴스 다중화**이며, 한 VM 안의 컨테이너 2개는 VM 수준 고가용성과 다름.
- Redis Pub/Sub을 제공하는 **배포 서비스가 지속 연결/구독을 지원하는지 별도 검증**(무료 서버리스 Redis는 제공 기능이 다를 수 있음).

## 8. 기능 상세: 일정 · 회의실 예약

### 8.1 [핵심] Calendar

- 오늘/개인/조직/회의 일정.
- 일정 생성·조회·수정·삭제.
- 주간/월간(필요하면 일간) 화면, 시간대와 시작·종료.
- 회의실 예약과 일정 연동, 일반 USER Today 카드에도 노출.
- Google Calendar 전체 기능 복제가 목적은 아님.

### 8.2 [핵심] Meeting Room

- 회의실 생성/조회/관리(관리자), 예약 가능 시간 조회, 예약 등록·취소·수정.
- **동일 회의실의 시간이 겹치는 예약 금지**.
- 겹침 기본 규칙: `기존 시작 < 새 종료 AND 기존 종료 > 새 시작`(반개구간 `[start,end)` 전제), `start < end` 검증.
- `UNIQUE(room_id, start_at)` 같은 단순 유니크 키만으로 임의 시간 구간의 겹침을 모두 막을 수는 없음.
- `@Transactional`만으로 동시 예약이 직렬화되는 것은 아님.

### 8.3 [실험] 동시성 제어

- 동일 방/시간대에 100개 등 동시 요청을 발생시키고 실제 무결성 위반 여부 확인.
- 후보: DB 제약·직렬화/잠금 정책, 낙관적 락(Optimistic), 비관적 락(Pessimistic), 필요시 Redis Distributed Lock.
- **DB 종류·구간 제약·동시성 실험 결과에 따라 최종 해법을 선정**. Redis 분산 락을 선도입하지 않음.
- 결과 지표: 중복예약 수/무결성, 성공·실패 건수, TPS/RPS, 평균/p95/p99, 락 대기/충돌률, 복잡도.
- 프로젝트에서 실제 채택하지 않은 기술도 실험 결과와 선택하지 않은 이유를 문서에 남길 수 있음.

## 9. 기능 상세: 메인 Today / 관리자 Business Dashboard

### 일반 USER 모드

- **오늘 일정**
- **안 읽은 메시지**
- **즐겨찾기 멤버**
- **회의실 예약 현황**
- 기존에 만든 홈 UI를 가급적 유지하면서 실제 API 데이터를 연결.

### 관리자 ADMIN 모드

- 기존 Today 자리를 **서비스 활동 대시보드**로 확장 (네이버웍스 메인을 그대로 복제하지 않음).
- **방문자 수/방문 이벤트**, **기간별 고유 활성 사용자(DAU 등)**, **현재 온라인 수(Presence)**, **신규 사용자**.
- **시간대별 접속/활동 그래프**, 메시지 전송량, 회의실 예약/이용량.
- 기간 **오늘/일간/7일/월간/연간/직접 선택** 및 **연 → 월 → 일 → 시간 Drill-down**.
- 그래프는 연·월·일·시간별 데이터가 실제로 맞게 집계되는지를 우선 검증.
- **방문 횟수 ≠ 고유 활성 사용자 수 ≠ 현재 온라인 수**. 각각 이벤트/집계 기준과 시간대 명시.
- 구현 초기에는 단순 DB 집계로 시작, 느려질 때 hourly/daily aggregate table 및 인덱스, 배치/집계 정책 검토.
- ADMIN 통계 데이터는 서버 API에서도 권한/회사 범위 제한.
- **Business Dashboard는 서비스 통계**, **System Monitoring은 서버 상태**라는 차이를 유지.

## 10. [후순위] 관리자 감사 로그 / 알림 외 기능

### 감사 로그 (Audit Log)
- 관리자 권한 변경, 회사/부서/구성원 관리, 민감한 관리자 작업의 **누가·언제·무엇을** 기록하는 방향.
- 개인정보·민감정보를 무분별하게 로그에 저장하지 않으며 보존/열람 권한을 정의.
- 필수 MVP 아님. RBAC 안정화 후 [후순위].

### 기타 우선순위
- 파일 첨부와 사용자 프로필 파일의 구체적 저장소(S3/Storage/로컬 개발) 선택은 미확정.
- 알림/감사로그/검색은 중요한 후속 기능이지만 **메신저 MVP와 DB 인증 구현을 지연시키지 않음**.

## 11. Observability (서버 Monitoring) + Performance

### 11.1 모니터링 스택 [후반 핵심]

- **Spring Boot Actuator → Micrometer → Prometheus → Grafana**.
- Prometheus는 메트릭 스크레이프·저장, Grafana는 시각화. 직접 Grafana를 프론트에서 다시 만들지 않음.
- 관리자 사이트에 'System Monitoring' 링크 정도는 추가 가능하나 **운영 관리 지표와 서비스 활동 통계를 구분**.
- 웹에 공개 시 Actuator/Grafana 관리 API 인증/접근제어 필수. 메트릭 Label로 고유 사용자ID/토큰 등 무제한·민감 정보를 남기지 않는다.

### 11.2 수집할 지표

- **HTTP API**: 전체/엔드포인트 템플릿별 호출 수, RPS/TPS, 평균·p95·p99 Latency, Error Rate, 상태코드 분포.
- **JVM/OS**: Heap/Non-Heap, GC, Thread 수, CPU, Memory.
- **DB/HikariCP**: Active/Idle/Max/Pending, 커넥션 획득 대기, 쿼리 응답시간·Slow Query(설정에 따라 별도 수집).
- **Redis**: 명령 호출 수·지연, Presence 수, Cache Hit/Miss(캐시 도입 시), Pub/Sub 발행/수신/실패(필요 시 Custom Metrics).
- **WebSocket**: 현재 연결 수, 연결/해제 수, 초당 메시지 수, 전달 실패 수(필요 시 Custom Metrics).
- **LLM**: 호출 수, 평균/p95, Timeout/Retry/실패, 토큰 수·비용 추정.
- Actuator 기본 메트릭에 없는 건 **Micrometer Custom Metric** 등으로 추가한다.

### 11.3 부하 테스트 / 병목 분석

- **k6 또는 JMeter 하나를 우선 선택**(양쪽 다 필수 아님).
- 부하 조건 예: 동시 사용자 100→500→1000, 실제 로컬 자원에 맞춰 조정.
- 시나리오: 로그인, 메시지 조회/발송, 회의실 동시 예약, WebSocket 연결/이벤트, 외부 API 지연.
- **API Latency와 JVM Thread, HikariCP Active/Pending, Redis, CPU를 함께 보며 병목 근거를 잡음**.
- 대량 메시지 데이터 10만~100만 이상 **통제 가능한 더미 데이터**로 Offset vs Cursor 비교.
- 정량 수치는 반드시 동일 환경·데이터 규모·부하 조건·측정 방식으로 Before/After 비교.
- **측정하지 않은 1만 TPS, 70% 개선, 서비스 운영 경험 등은 기재하지 않는다**.
- 무료 배포 서버에 대량 부하를 가하는 대신 **로컬 Docker나 별도 허용된 테스트 환경**에서 진행.

## 12. [후반] AI 채팅 요약 및 외부 API 안정성

- AI 기능은 **채팅방 대화 요약 1개**를 메인으로 구현. 메시지 초안/공지 요약/조직 검색 등은 아이디어로만 논의했고 초기 범위에 넣지 않음.
- 채팅방 접근 권한 있는 사용자가 허용된 메시지 범위만 요약 요청 → Spring Backend가 DB에서 메시지 조회 → 외부 LLM API → 요약 반환/필요시 저장.
- **AI 모델 자체 학습, vLLM 서빙, RAG, Agent는 지금 목표 아님**.
- 외부 서비스 특성(느린 응답, 실패, Rate Limit, 비용, 개인정보 전달)을 백엔드에서 관리.
- 후보: 적절한 **Timeout**, 제한된 **Retry(백오프/재시도 가능 오류 구분)**, **Fallback**, **Rate Limiting**, **동일 메시지 구간 캐시**, 장시간 작업의 **Async/작업 큐**, Metrics·Token/Cost 관리.
- Queue/Backpressure/Circuit Breaker/Batching은 별도 공고 분석에서 배운 **추가 실험 주제 후보**. 실제 필요 없는데 무조건 넣지 않는다.
- 외부 LLM으로 회사/대화 내용을 넘길 때 테스트 데이터/개인정보·고지·비용을 고려. API 키는 백엔드 환경변수만.
- 외부 실패가 일반 채팅/로그인 API 전체 장애로 번지지 않도록 실패 격리 검토.

## 13. 기술 스택 전체 및 선정 이유/상태

| 분류 | 기술 | 지위/선정 근거 |
|---|---|---|
| Backend Language | **Java** | [핵심] 실무 주력 언어. OOP/예외/컬렉션/동시성 깊이 학습 |
| Framework | **Spring Boot** | [핵심] REST, Security, JPA, WebSocket 통합. **Boot 3 기반 학습 선호**, 앱의 실제 버전은 확인·지원 상태 검토 후 결정 |
| Web | **Spring MVC** | [핵심] 요청/응답, Filter, DispatcherServlet, Controller 흐름 |
| Auth | **Spring Security** | [핵심] 인증/인가, 조직/리소스 RBAC, USER/ADMIN 데모 |
| ORM | **Spring Data JPA / Hibernate** | [핵심] 엔티티·영속성 컨텍스트·변경 감지·연관관계·N+1 |
| Query | **QueryDSL** | [필요 시] 동적 복합조회, 메시지 검색/목록/일정; 처음부터 모든 쿼리에 강제하지 않음 |
| RDBMS | **MySQL 또는 PostgreSQL** | [미확정] **하나만 선택**. MySQL 실무/학습 vs Supabase PostgreSQL 배포 |
| Redis | **Redis** | [핵심/후반] Presence TTL, 다중 서버 Pub/Sub, 캐시는 측정 후 |
| Real-time | **WebSocket + STOMP** | [핵심] 채팅·반응·읽음 등 양방향 이벤트 |
| Frontend | **Next.js + TypeScript** | [핵심] 기존 UI 재사용, 실시간 앱 화면, JS보다 타입 안정성 |
| UI | **Tailwind CSS / shadcn/ui** | [계획] 빠른 일관된 컴포넌트 개발. 실제 의존성 확인 |
| Data Fetch | **TanStack Query** | [계획] REST 서버 상태, 캐싱, 로딩/오류 관리 |
| Front State | **Zustand** | [선택] 실제 전역 UI 상태가 복잡해질 경우만 |
| Client WebSocket | **STOMP 클라이언트** | [계획] 서버 STOMP와 연결 |
| Dashboard Charts | **차트 라이브러리** | [미확정] 기간별 방문/활동 그래프 용. Recharts 등 후보이나 선정되지 않음 |
| Infra | **Docker / Docker Compose** | [핵심] DB/Redis/백엔드 다중 인스턴스 재현 |
| Reverse Proxy | **Nginx** | [후반] LB, WebSocket 프록시, 스케일아웃 실험 |
| Scale | **Spring Boot 인스턴스 2개** | [후반] 하나의 코드베이스로 분산 통신을 재현 |
| Unit/Test | **JUnit 5 / Spring Boot Test** | [핵심] 도메인/Controller/Repository 통합 검증 |
| Integration | **Testcontainers** | [검토] 실제 RDBMS/Redis 컨테이너로 통합 테스트 |
| Load | **k6 또는 JMeter** | [검토 후 하나] 동시성·병목·성능 실험 |
| Metrics | **Actuator / Micrometer** | [후반] Spring 메트릭/커스텀 지표 노출 |
| Metrics Store | **Prometheus** | [후반] 지표 수집/저장 |
| Dashboard | **Grafana** | [후반] 인프라/APM 유사 시각화 |
| AI | **External LLM API** | [후반] 채팅 요약 및 외부 API 회복력 |
| CI/CD | **GitHub Actions 등** | [후순위/선택 미정] 빌드·테스트·배포 자동화 후보 |
| Front Hosting | **Vercel** | [무료 배포 후보] Next.js |
| API Hosting | **Render** | [무료 배포 후보] Spring 서비스 |
| Managed PG | **Supabase** | [무료 배포 후보] PostgreSQL/스토리지 후보; 사용 여부 미정 |
| Managed Redis | **Upstash** | [무료 배포 후보] 비용 장점, Pub/Sub·지속 연결 지원 반드시 확인 |
| Alternative Host | **AWS EC2/RDS/S3/ALB** | [후반 실습 후보] 필요시 임시 구성, 비용 발생 검토 |
| Other | **MyBatis** | [학습 비교 대상] 회사에서 사용하더라도 이 개인 프로젝트의 주력 영속 계층은 JPA; 둘 다 강제로 넣지 않음 |

### Spring/JPA 핵심 학습/실습 체크

- Spring: IoC/DI, Bean/Scope/Component Scan, Filter/Interceptor, DispatcherServlet, Proxy/AOP, `@Transactional`(self invocation), `REQUIRED` vs `REQUIRES_NEW`, Security 인증/인가.
- JPA: EntityManager/영속성 컨텍스트, 엔티티 생명주기, 1차 캐시, Dirty Checking, flush/commit, 연관관계 주인, LAZY/EAGER, N+1, Fetch Join/DTO 조회.
- DB: ACID, Transaction isolation, 인덱스, 실행계획, Composite Index, Optimistic/Pessimistic Lock, Deadlock, Offset/Cursor.
- HTTP/OS: method·headers·cache·connection, thread pool, connection pool, race condition, timeout.
- Spring Boot 3 책을 읽어도 원리 학습이 목표이고, **공부 완료까지 개발을 미루지 않는다**.

## 14. 현재부터 개발 순서 (권장 및 완료 조건)

> 모든 단계는 **짧은 수직 슬라이스(프론트 UI ↔ API ↔ DB ↔ 테스트)**로 나눠 진행. 순서는 요구사항 의존성에 따라 일부 조정 가능.

### Phase 0 — 지금 즉시: 저장소 읽기/현황 브리핑 [수정 금지]
- 두 Git 저장소의 현 브랜치, 변경사항, 빌드 파일, 실제 소스, README, AGENTS, 디자인/실행 명령 확인.
- 로그인 화면, `admin / 1234` 임시 로직, 홈 UI, 소셜 로그인 버튼의 실제 코드 위치 확인.
- DB, API, Security, 프로젝트 초기화 여부 조사.
- **출력**: 현재 구현/미구현, 사용 중인 버전, 후보 결정 사항, Phase 1 최소 작업 및 예상 수정 파일/테스트.
- **사용자가 “진행해” 하기 전에는 코드/설정/문서 수정하지 않음**.

### Phase 1 — Spring Boot + RDBMS + ID/PW 실로그인 [첫 구현]
- Spring 빌드 환경/패키지 구조 검토 → Controller/Service/Repository/DTO/Validation/예외 처리/프로파일.
- **MySQL vs PostgreSQL**, **세션 vs JWT**, Boot 버전·빌드 도구만 우선 의사결정.
- User·Role 및 필요한 최소 Company/Member 관계와 DB 마이그레이션 검토.
- 안전한 비밀번호 해시, 로그인/로그아웃/내 정보/필요 시 회원가입 API 및 테스트.
- 기존 Next.js 로그인 UI에서 API 호출 → DB 계정 검증 → 로그인 상태 유지 → 기존 홈 이동.
- 프론트 `admin / 1234` 임시 분기 제거. 버튼 UI 유지, 미구현 OAuth 버튼은 명확히 표시.
- **종료 조건**: 브라우저 새로고침·로그아웃·실패 인증까지 실제 서버로 작동.

### Phase 2 — RBAC + 상단 USER/ADMIN 데모 전환
- `ROLE_USER`/`ROLE_ADMIN` 실제 Backend API 보호, 401/403 테스트.
- 상단 모드 버튼 → 확인 모달(취소/승인) → 데모 계정/세션 전환 → 사용자 정보/API/홈 UI 갱신.
- 운영용 권한 승격 API와 분리, 데모 데이터 보호.
- **종료 조건**: 일반 USER 관리자 API 403, DEMO ADMIN 권한 통과, 두 방향 전환 및 UI 동기화.

### Phase 3 — 회사/부서/구성원·조직도
- CRUD/검색/프로필/회사 데이터 격리, 회사별 관리자 기능, 기본 테스트.
- 멤버 즐겨찾기는 핵심 도메인 안정 후 메신저 상세 Phase에서 해도 됨.

### Phase 4 — Messenger MVP (단일 서버)
- 1:1/그룹 방, 참가자·방 목록, 메시지 저장/송수신, WebSocket/STOMP, 안읽음/읽음, 과거 메시지 Cursor 조회.
- 두 브라우저/서로 다른 계정으로 실시간 통신/재연결/DB 영속 검증.
- API·WebSocket 구독·메시지 발행의 참가 권한 검사.
- **종료 조건**: 채팅이 실시간 + DB 조회 + 인증/인가 + 기본 테스트에서 정상.

### Phase 5 — Messenger UX 확장
- 답장, 수정/삭제, 전달(스냅샷), 복사, Reaction, 공지, 파일 첨부.
- 개인별 채팅방 Pin, 멤버 Favorite, 읽음 수 개선, 메시지 검색은 필요시.
- **알림 시스템**은 이 단계 말미 또는 별도 후순위 단계로 분리.
- 공지 권한/원본 삭제, 첨부 저장소/파일 접근 제어 정책 정리.

### Phase 6 — Presence/Redis
- Redis 연결, TTL/Heartbeat, 일반·비정상 종료, 다중 탭/기기.
- 온라인/오프라인 이벤트 및 상태 조회 API, stale 제거 테스트.

### Phase 7 — Spring 2대/Nginx/Redis Pub/Sub
- **동일 백엔드 레포**로 인스턴스 2개, Nginx LB.
- 서로 다른 서버 연결 시 이벤트 전달 실패 재현 → Redis Pub/Sub 도입.
- 중복 이벤트, 재연결, DB 영속/발행 순서, 다운된 구독자 한계 검증.
- **운영 배포 무료 Redis가 Pub/Sub을 지원하는지 미리 확인**. 필요한 경우 로컬 Docker Redis 실험과 배포 구성 분리.

### Phase 8 — Calendar/Meeting Room/Concurrency
- 일정/회의실 CRUD, 시간구간 검증, 그룹/개인 일정.
- 동시 예약 Race Condition 재현, DB/Optimistic/Pessimistic/Distributed Lock 비교.
- 실제 일관성을 보장할 방법 선택 후 테스트, 측정 결과 기록.

### Phase 9 — USER Today + ADMIN Business Dashboard
- 기존 홈 UI 연결 및 관리자용 활동 통계 그래프.
- 방문·고유 활성·온라인·신규·메시지·예약 각각 정의.
- 연/월/일/시간 조회/기간 선택 및 접근 제어.
- 성능 문제 확인 시 인덱스·사전 집계.

### Phase 10 — 성능 분석 + Monitoring
- JPA N+1/Fetch Join/DTO, Offset vs Cursor와 인덱스, HikariCP 병목 등을 실제 테스트.
- Actuator, Micrometer, Prometheus, Grafana로 API·JVM·DB·Redis·WebSocket 지표.
- k6/JMeter 부하테스트 및 성능 전후 문서화.

### Phase 11 — AI 채팅 요약
- 채팅방 권한 검사 → 메시지 취합 → LLM API → 요약.
- Timeout/Retry/Fallback/Rate limit/Cache/Async/비용·Metrics 중 필요한 것 적용, 실패 격리/개인정보 검토.

### Phase 12 — 소셜 로그인 / 알림 / 감사 로그 / CI/CD / 최종 배포
- 네이버·카카오·구글 OAuth2 실연동(일정은 기본 로그인 완성 후 결정).
- 앱 내 알림(메시지/예약 등), 관리자 Audit Log **후순위**, 일정·역량에 따라 우선순위 결정.
- Docker Compose, GitHub Actions 등 CI/CD(도구 선택 미정), Vercel/Render/Supabase/Upstash 또는 대안 검토.
- 공개 데모 링크·Demo USER/ADMIN 체험·안정성·README/트러블슈팅 문서 정리.

## 15. Engineering Deep Dive / 포트폴리오 후보

아래는 **미래에 실제 재현하고 측정하면 기록할 Case 후보**. 발생하지 않은 사건을 미리 성과로 작성하지 않는다.

1. **JPA N+1**: 채팅방과 참여자 목록 N+1 확인 → Fetch Join/DTO/Batch Size 비교 → 쿼리 횟수·p95.
2. **조회 최적화**: Offset 큰 위치 vs `id < cursor`, `(room_id,id)` 인덱스, EXPLAIN, 10만/100만 데이터, p95/p99.
3. **실시간 Scale-out**: 서로 다른 앱 인스턴스에 연결된 사용자 간 메시지 전달 문제 → Redis Pub/Sub, 영속성·중복/유실 분석.
4. **Presence stale**: 강제 종료/네트워크 실패/서버 다운 → TTL/Heartbeat, 여러 기기 접속, 감지 지연.
5. **동시 예약**: 범위 겹침 + 동시 요청 → DB/락 제어, 중복 0 보장 확인, TPS/latency/Lock wait.
6. **JPA 트랜잭션**: Dirty Checking, flush, 프록시/self invocation, REQUIRED/REQUIRES_NEW를 관련 기능 테스트로 설명.
7. **DB/HikariCP 병목**: RPS 증가 → 커넥션 풀 포화·p95 변화 → 근본 원인 개선 및 재측정.
8. **Redis 캐시**: 실제 반복 조회 병목 시 Cache Hit/Miss, invalidation, DB 결과 정합성 및 응답시간 비교.
9. **Dashboard 집계**: Raw Access/Event GROUP BY 성능 → 인덱스·hourly/daily aggregate 검토/측정.
10. **외부 LLM 지연·실패**: timeout/retry/backoff/async/cache/rate limit 전후 지연·실패·비용 비교.
11. **배포/장애**: 앱 1개 종료, Redis Pub/Sub 구독 끊김, WebSocket 재연결 등을 재현해 동작/한계 문서화.
12. **인증/인가**: 직접 URL·STOMP 목적지 변경으로 권한 우회 시도 → 401/403·타사 리소스 접근 차단 테스트.

문서 템플릿:

```text
1. 문제/가설
2. 재현 조건·테스트 환경·데이터 규모
3. Before(실측)
4. 로그/실행계획/메트릭 근거
5. 원인 분석
6. 대안 및 트레이드오프
7. 선택한 해결 방식
8. 구현/테스트
9. After(동일 조건 실측)
10. 한계·배운 점·추후 검토
```

## 16. 공개 배포의 현실적 제약

후보 아키텍처:

```text
Browser
   |
Vercel / Next.js
   |
Nginx (후반 실험 또는 별도 배포)
  /   \
Spring #1  Spring #2          # 하나의 백엔드 Repository
   \   /
RDBMS + Redis
   |
External LLM API (Spring에서 호출)

Spring Actuator/Micrometer -> Prometheus -> Grafana
```

- 검토해온 무료 후보: **Vercel Hobby + Render Free + Supabase Free(PostgreSQL) + Upstash Redis Free**.
- **공개 데모 상시 접근성은 무료 정책으로 보장되지 않음.** Render Sleep/Cold Start, Workspace 공용 실행시간, Supabase Pause, Redis 명령 제한 등 정책은 배포 당시 공식 문서로 재확인.
- Nginx·Spring1·Spring2를 모두 무료 Render 서비스로 운영할 때 **합산 실행시간 한도**에 유의, 24시간 억지 keep-alive 권장하지 않음.
- Redis 제공 방식(특히 Upstash)이 **Spring STOMP 다중 서버 메시징용 Pub/Sub 지속 구독**을 지원하는지 검증 전에는 배포 구조를 확정하지 않는다.
- 단일 서버 공개 데모 + 로컬 Docker 2서버 Scale-out 실험을 **분리하는 전략도 허용**.
- AWS EC2/RDS/S3/ALB는 추후 임시 운영/실험 후보. 비용이 발생할 수 있으므로 사용자 승인 후.
- Nginx 하나가 죽으면 그 뒤 앱이 살아 있어도 접근 불가한 SPOF가 될 수 있다. **앱 인스턴스 2개 = 완전 고가용성**은 아니다.
- 이미지/첨부·외부 LLM·WebSocket/세션·CORS·쿠키 도메인 정책은 배포 전 실제 테스트.
- 사용자는 콜드스타트 지연을 어느 정도 허용하지만, 면접관이 언제든 볼 수 있는 공개 데모를 원하므로 무료 운영의 현실적 한계를 명시.

## 17. 문서/GitHub/이력서 원칙

- GitHub **루트 README.md = 방문자용 간판**: 한 줄 소개, 시연/데모, 현재 상태, 핵심 성과 4~5개, 아키텍처, 기술, 주요 문서 링크.
- **`docs/PROJECT_CONTEXT.md` = 상세 설계/요구사항**, `docs/PROGRESS.md` = 실제 진행 현황, `docs/FEATURE_CHECKLIST.md` = 전 기능 구현 여부.
- 트러블슈팅 문서는 `docs/troubleshooting/` 등으로 별도 분리; Before/After 실측, 환경, 링크, 코드 증거 포함.
- 이력서에는 기능 20개 나열보다 **실제로 해결한 대표 기술 문제 4~5개**를 짧게 기재.
- “트래픽 대응, TPS XX% 개선, Kafka 도입”은 실제 구현·측정했을 때만 기재.
- 서술에서 ‘네이버웍스 아키텍처를 복제’라는 부정확한 표현 금지. ‘네이버웍스의 협업 기능을 참고’라고 표현.

## 18. 공부 방식 (에이전트 설명 수준)

- 사용자는 Java/Spring Boot 웹개발 약 1년차이며 JPA·Spring 내부 원리를 보강 중, Next.js/TypeScript는 구현하며 학습.
- 프로젝트 시작 전 독서/학습 목표: **《HTTP 완벽 가이드》 핵심 ~400p → 《주니어 백엔드 개발자가 반드시 알아야 할 실무 지식》 → Spring Boot 3 기반 Spring/JPA 핵심 실습 → 프로젝트**.
- 학습 단계가 끝날 때까지 코딩을 미루지 않는다. **필요한 순간 책/공식문서 참고**: JPA 원리(《자바 ORM 표준 JPA 프로그래밍》), DB/인덱스/락(《Real MySQL 8.0》 또는 선택 DB 전문자료), OS/스레드(《혼자 공부하는 컴퓨터 구조+운영체제》), 객체 설계(《객체지향의 사실과 오해》), Java(《Effective Java》/《자바의 정석 4판》 부분 복습), 품질(《자바 코드의 품질을 높이는 100가지 방법》, 《Clean Code》 필요한 부분).
- 에이전트는 해결책과 함께 **왜 선택했는지, 내부 동작, 예상 SQL, 대안/단점, 테스트 방법, 면접 질문**을 이해하기 쉬운 언어로 설명.
- 과잉 추상화/마이크로서비스를 강요하지 않고 작고 작동하는 수직 슬라이스부터 구현.

## 19. 확정되지 않은 결정 / 승인 체크

- [ ] 백엔드 실제 Java 및 Spring Boot 버전, 빌드 도구(Gradle/Maven)
- [ ] 메인 DB: MySQL vs PostgreSQL, 로컬/배포 동일 전략
- [ ] 인증 방식: 서버 세션+보안 쿠키 vs JWT(+갱신/저장 전략)
- [ ] Demo USER/ADMIN 별도 세션·계정 전환 정책, 공개 관리자 쓰기 권한 안전장치
- [ ] 조직 Company/Department/User/Membership/Role ERD, 다중회사·초대 정책
- [ ] DB 마이그레이션 도구(Flyway/Liquibase 등) 채택 여부
- [ ] API 계약 및 에러 응답/인증 상태 계약
- [ ] 메시지 읽음 모델, 순서/중복/재전달 기준, 채팅방 운영 권한
- [ ] Reaction과 수정/삭제/전달/공지의 정확한 도메인 정책
- [ ] 파일 스토리지, 확장자/크기/백업/보관 정책
- [ ] Presence 키·연결 식별자·Heartbeat/TTL 값·다중 탭 정책
- [ ] Redis Pub/Sub 구현 방식과 무료 Redis 배포 호환성
- [ ] 회의실 시간 구간·시간대·DB 원자성/락 전략
- [ ] 사용자 활동 지표(방문자·활성 사용자) 수집 이벤트 정의와 집계 단위
- [ ] Frontend 라우터/스타일링/상태관리 라이브러리의 실제 설치 현황
- [ ] Grafana 운영 위치/공개 접근 제어/메트릭 보존
- [ ] 외부 LLM 공급자·개인정보·비용/요약 범위
- [ ] 네이버/카카오/구글 OAuth 실제 구현 시점
- [ ] 알림/감사 로그 구현 범위와 후순위 일정
- [ ] CI/CD 도구, 공개 배포/인프라·비용/데모 데이터 리셋
- [ ] 문서 자동 업데이트 범위, 테스트/코딩 컨벤션 세부
- [ ] 작업별 사용자 '진행해' 승인 여부

## 20. 코드 에이전트에게 요구하는 다음 응답 (현재)

**지금은 Phase 0만 수행. 읽기 전용 탐색은 가능하지만 사용자의 “진행해” 전에는 파일·코드·설정·문서를 수정하지 말 것.**

1. 두 저장소의 실제 상태, 로그인/홈 UI 및 임시 `admin/1234` 위치, 소셜 버튼 UI 위치, 빌드/버전/DB/API 존재 여부를 근거 파일 경로와 함께 보고.
2. 현재 완료/미완료와 이 문서의 계획/실제 코드 간 차이를 명시.
3. **첫 구현은 프론트 로그인 → Spring Boot 로그인 API → DB 검증 → 인증 상태 유지 → 기존 홈 이동 → 로그아웃**.
4. 필요한 **최소** 의사결정: Boot 버전, 메인 DB, 세션 vs JWT. 후보 장단점/추천을 제시.
5. Phase 1을 작고 검증 가능한 작업으로 쪼개 **예상 파일, API 계약, 테이블, 테스트, 빌드/실행 절차**를 보고.
6. 사용자의 명시적 **“진행해”** 승인을 기다린 뒤 그 범위에서만 코드를 변경.
7. 커밋/푸시는 별도 지시 전 금지. 기존 로그인/홈 UI 및 소셜 버튼 UI를 임의로 다시 만들거나 지우지 말 것.

---

**결정적 원칙: 기능 이름을 나열하는 프로젝트가 아니라, 작동하는 제품과 재현 가능한 기술적 문제 해결 경험을 쌓는 프로젝트. 초반에는 오직 인증 API 연결부터 시작한다.**
