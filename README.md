<div align="center">

# NaverWorks Clone · Backend

### 실시간 메신저 · 조직 기반 권한 · 일정과 회의실 예약을 하나의 흐름으로 잇는 협업 플랫폼의 서버

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Pub%2FSub-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-010101?style=for-the-badge)

`Backend Repository` · `Personal Portfolio Project` · [Frontend Repository →](https://github.com/wjsgusdn12/naverworks_clone_front)

</div>

> 네이버웍스의 협업 기능을 참고해 설계한 개인 프로젝트입니다. 특정 서비스의 내부 구현이나 디자인을 복제하지 않습니다.
> 이 문서는 프로젝트의 **전체 설계**를 그림으로 보여 줍니다.

---

## 1. 전체 구조

브라우저는 Next.js 화면과 대화하고, 화면은 REST와 WebSocket 두 통로로 이 서버와 대화합니다. 메시지·예약처럼 남아야 하는 데이터는 PostgreSQL에, 실시간 전파와 접속 상태처럼 흘러가는 데이터는 Redis에 둡니다.

<!-- diagram: architecture -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart LR
    Browser["브라우저"]
    Front["Next.js 프론트"]
    subgraph API["Spring Boot API"]
        Security["Spring Security · JWT"]
        REST["REST 컨트롤러"]
        STOMP["STOMP 브로커"]
        Service["도메인 서비스"]
        JPA["JPA · Flyway"]
    end
    DB[("PostgreSQL<br/>Supabase")]
    Redis[("Redis<br/>Pub/Sub · Presence")]
    LLM["외부 LLM API"]

    Browser --> Front
    Front -->|"HTTPS · REST"| Security
    Front -.->|"WebSocket · STOMP"| Security
    Security --> REST --> Service
    Security --> STOMP --> Service
    Service --> JPA --> DB
    Service -.-> Redis
    Service -.->|"요약 요청"| LLM
```

---

## 2. 배포 구조

비용 0원을 원칙으로 잡았습니다. 상시 가동이 필요한 서버 묶음은 Oracle 무료 VM 한 대에 docker-compose로 올리고, 프론트와 DB는 관리형 무료 서비스를 씁니다.

<!-- diagram: deployment -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart TB
    User["사용자"]
    subgraph Vercel["Vercel (무료)"]
        Next["Next.js"]
    end
    subgraph VM["Oracle Cloud VM (Always Free) · docker-compose"]
        Nginx["nginx<br/>TLS · 라우팅 · 분배"]
        API1["api-1"]
        API2["api-2"]
        RedisC[("redis")]
        Nginx --> API1
        Nginx --> API2
        API1 <-.-> RedisC
        API2 <-.-> RedisC
    end
    Supa[("Supabase PostgreSQL<br/>(무료)")]
    DNS["DuckDNS<br/>무료 서브도메인 2개"]
    GH["GitHub Actions → GHCR"]

    User --> DNS
    DNS --> Next
    DNS --> Nginx
    Next -->|"REST · WebSocket"| Nginx
    API1 --> Supa
    API2 --> Supa
    GH -.->|"이미지 pull"| VM
```

nginx가 인증서와 WebSocket 업그레이드를 맡고, 같은 이미지로 뜬 api 두 대 사이는 Redis가 이어 줍니다. Supabase의 7일 무활동 정지는 서버의 일일 keep-alive와 GitHub Actions 스케줄로 막습니다.

---

## 3. 도메인 지도

메신저가 중심이고, 조직·인증과 일정·예약이 나머지 두 축입니다. 운영 관점의 관측성과 AI 요약은 그 위에 얹는 층입니다.

<!-- diagram: domain-map -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart TB
    Core(("NaverWorks Clone"))
    Core --> Identity["조직 · 인증<br/>회사 · 부서 · 구성원 · 역할<br/>JWT · 데모 모드"]
    Core --> Chat["메신저<br/>1:1 · 그룹 · 나와의 채팅<br/>읽음 · 답장 · 수정 · 삭제<br/>전달 · 공지 · 리액션 · 멘션"]
    Core --> Schedule["일정 · 회의실<br/>개인 · 조직 일정<br/>예약 · 동시성 제어"]
    Core --> Home["Today · 관리자 대시보드<br/>오늘 일정 · 안 읽은 메시지<br/>활동 통계 · Drill-down"]
    Core --> Realtime["실시간 · 접속 상태<br/>STOMP · Presence TTL<br/>Redis Pub/Sub"]
    Core --> Ops["운영<br/>알림 · 감사 로그<br/>Actuator · Prometheus · Grafana"]
    Core --> AI["AI<br/>채팅방 대화 요약<br/>Timeout · Retry · Fallback"]
```

프로젝트·태스크 관리, MSA, Kafka, Kubernetes는 의도적으로 범위에서 뺐습니다. 문제가 재현되기 전에는 도입하지 않습니다.

---

## 4. 데이터 모델

회사 → 부서 → 구성원의 조직 계층이 모든 데이터의 울타리입니다. 메시지는 채팅방에, 읽음 위치는 참여자 행에, 부가 정보(멘션·리액션·공지)는 별도 테이블에 둡니다.

<!-- diagram: erd -->
```mermaid
---
config:
  markdownAutoWrap: false
---
erDiagram
    COMPANY ||--o{ DEPARTMENT : "포함"
    COMPANY ||--o{ MEMBERSHIP : "소속"
    DEPARTMENT ||--o{ MEMBERSHIP : "배치"
    USER_ACCOUNT ||--o{ MEMBERSHIP : "계정"
    JOB_GRADE ||--o{ MEMBERSHIP : "직급"
    MEMBERSHIP ||--o{ REFRESH_TOKEN : "세션"
    MEMBERSHIP ||--o{ MEMBER_FAVORITE : "즐겨찾기"
    MEMBERSHIP ||--o{ SEARCH_HISTORY : "검색 기록"

    CHAT_ROOM ||--o{ CHAT_ROOM_MEMBER : "참여"
    MEMBERSHIP ||--o{ CHAT_ROOM_MEMBER : "참여자"
    CHAT_ROOM ||--o{ CHAT_MESSAGE : "메시지"
    MEMBERSHIP ||--o{ CHAT_MESSAGE : "보낸 사람"
    CHAT_MESSAGE ||--o{ CHAT_MESSAGE_MENTION : "멘션"
    CHAT_MESSAGE ||--o{ CHAT_MESSAGE_REACTION : "리액션"
    CHAT_MESSAGE o|--o{ CHAT_MESSAGE : "답장 · 전달 원본"
    CHAT_ROOM ||--o{ CHAT_ROOM_NOTICE : "공지"
    MEMBERSHIP ||--o{ NOTIFICATION : "알림"

    MEMBERSHIP ||--o{ CALENDAR_EVENT : "일정"
    MEETING_ROOM ||--o{ RESERVATION : "예약"
    MEMBERSHIP ||--o{ RESERVATION : "예약자"

    CHAT_ROOM_MEMBER {
        bigint last_read_message_id "읽음 커서"
        timestamptz pinned_at
        string notification_mode
    }
    CHAT_MESSAGE {
        string message_type "TEXT · SYSTEM"
        bigint reply_to_message_id
        bigint forwarded_from_message_id
        timestamptz edited_at
        timestamptz deleted_at "논리 삭제"
        long version "낙관적 락"
    }
    RESERVATION {
        timestamptz start_at "[start, end)"
        timestamptz end_at
    }
```

삭제는 전부 논리 삭제입니다.

---

## 5. 인증과 세션

Access Token은 3시간, Refresh Token은 최초 로그인 기준 최대 7일입니다. Refresh는 회전하지만 세션의 절대 만료 시각은 늘어나지 않아, 한 번 로그인하면 7일 뒤에는 반드시 다시 로그인합니다.

<!-- diagram: auth-flow -->
```mermaid
---
config:
  markdownAutoWrap: false
  sequence:
    actorMargin: 40
    messageMargin: 44
    mirrorActors: false
    wrap: false
---
sequenceDiagram
    participant U as 사용자
    participant F as Next.js
    participant A as API
    participant D as PostgreSQL

    U->>F: 아이디 · 비밀번호
    F->>A: POST /auth/login
    A->>D: 계정 조회 · 해시 비교
    A->>D: Refresh Token 해시 저장 (세션 만료 7일)
    A-->>F: Access Token (3h) + Refresh Token
    F-->>U: 홈 화면

    Note over F: 만료 1분 전 자동 갱신
    F->>A: POST /auth/refresh
    A->>D: 기존 토큰 폐기 · 새 토큰 저장 (만료 시각 유지)
    A-->>F: 새 Access · Refresh

    F->>A: POST /auth/logout
    A->>D: Refresh Token 폐기
```

토큰 원문은 DB에 남기지 않고 해시만 저장합니다. 화면의 메뉴 숨김은 UX일 뿐이고, 권한 판단은 항상 서버의 `ROLE_USER` · `ROLE_ADMIN` 검사가 근거입니다.

---

## 6. 메시지 전송과 실시간 전파

메시지는 먼저 DB에 저장되고, 트랜잭션이 커밋된 뒤에만 실시간 이벤트가 나갑니다. 그래서 받는 쪽이 이벤트를 보고 조회해도 데이터가 없는 경우가 생기지 않습니다.

<!-- diagram: message-flow -->
```mermaid
---
config:
  markdownAutoWrap: false
  sequence:
    actorMargin: 40
    messageMargin: 44
    mirrorActors: false
    wrap: false
---
sequenceDiagram
    participant A as 보낸 사람 화면
    participant API as API (api-1)
    participant DB as PostgreSQL
    participant R as Redis Pub/Sub
    participant API2 as API (api-2)
    participant B as 받는 사람 화면

    A->>API: POST /chatRoom/message
    API->>DB: 메시지 저장 · 보낸 사람 읽음 커서 전진
    API->>DB: 알림 생성 (방 설정 · 멘션 규칙)
    DB-->>API: commit
    Note over API: AFTER_COMMIT 이벤트
    API-->>A: 응답 (메시지 · 읽음 수)
    API->>R: publish MESSAGE_CREATED
    R-->>API: 자기 세션에 전달
    R-->>API2: 다른 서버 세션에 전달
    API-->>B: /topic/chatRoom/{id}
    API2-->>B: /topic/chatRoom/{id}
```

한 대로 돌 때는 Redis 없이 서버 안 브로커가 같은 역할을 합니다. 이벤트 종류는 MESSAGE_CREATED · UPDATED · DELETED · REACTION_UPDATED · NOTICE_UPDATED · READ_UPDATED · TYPING_CHANGED입니다.

---

## 7. 읽음 수 계산

메시지마다 "누가 읽었는지"를 저장하지 않습니다. 참여자마다 마지막으로 읽은 메시지 ID 하나만 두고, 읽음 수는 그 커서들로 계산합니다. 저장은 가볍고 계산은 쿼리 한 번입니다.

<!-- diagram: read-cursor -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart LR
    subgraph Room["채팅방 참여자"]
        A["A · last_read = 120"]
        B["B · last_read = 118"]
        C["C · last_read = 120"]
    end
    M119["메시지 119<br/>(A가 보냄)"]
    M120["메시지 120<br/>(C가 보냄)"]

    A -.->|"보낸 사람 제외"| M119
    B -->|"118 < 119 → 안 읽음"| M119
    C -->|"120 ≥ 119 → 읽음"| M119
    A -->|"읽음"| M120
    B -->|"안 읽음"| M120
    C -.->|"보낸 사람 제외"| M120

    M119 --> R1["읽음 1 · 안 읽음 1"]
    M120 --> R2["읽음 1 · 안 읽음 1"]
```

화면도 같은 규칙으로 계산합니다. 서버가 보내는 READ_UPDATED 이벤트는 "누가 어디까지"만 담고, 말풍선의 숫자는 화면이 커서로 다시 셉니다. 이벤트 순서가 뒤바뀌어도 숫자가 어긋나지 않습니다.

---

## 8. 메시지 확장 기능 규칙

답장·수정·삭제·전달·공지·리액션은 모두 같은 원칙을 따릅니다. 원본은 보존하고, 시간 제한은 24시간, 권한은 서버가 확인합니다.

<!-- diagram: message-rules -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart TB
    Msg["TEXT 메시지"]
    Msg --> Reply["답장<br/>원문 내용 스냅샷 보존<br/>원문 삭제 시 '삭제된 메시지'"]
    Msg --> Edit["수정<br/>본인 · 24h 이내<br/>version으로 충돌 감지"]
    Msg --> Delete["삭제<br/>본인 · 24h 이내 · 논리 삭제<br/>공지였다면 자동 내림"]
    Msg --> Forward["전달<br/>참여 중인 방 최대 10개<br/>새 메시지로 복사 + 원본 ID"]
    Msg --> Notice["공지<br/>모든 참여자 등록 가능<br/>방당 여러 개 · 내리기 · 다시 보지 않기"]
    Msg --> React["리액션<br/>1인 1이모지 · 24h 이내<br/>누른 사람 목록"]
    Msg --> Mention["멘션<br/>@구성원 · @All<br/>알림 규칙과 연결"]
    System["SYSTEM 메시지<br/>초대 · 퇴장 · 공지 안내"] -.->|"읽음 · 공지 · 리액션 대상 아님"| Msg
```

---

## 9. 접속 상태 (Presence)

온라인 여부는 서버 메모리가 아니라 Redis의 TTL 키로 판단합니다. 비정상 종료로 disconnect가 오지 않아도 TTL이 만료되면 저절로 오프라인이 됩니다.

<!-- diagram: presence -->
```mermaid
---
config:
  markdownAutoWrap: false
---
stateDiagram-v2
    [*] --> Offline
    Offline --> Online : WebSocket 연결<br/>Redis 키 + TTL 저장
    Online --> Online : Heartbeat로 TTL 갱신<br/>탭이 늘면 연결 수만 증가
    Online --> Offline : 마지막 탭 정상 종료<br/>즉시 삭제
    Online --> Offline : 네트워크 끊김 · 강제 종료<br/>TTL 만료
    Online --> Online : 탭 하나만 닫힘<br/>연결 수 감소 · 온라인 유지
```

TTL 때문에 오프라인 감지에는 약간의 지연이 있습니다. "즉시 정확한 상태"가 아니라 "근사한 상태"라고 표현합니다.

---

## 10. 서버 2대와 Scale-out

같은 코드로 인스턴스 2개를 띄우고 nginx가 나눠 받습니다. 서버 메모리 안 브로커만 쓰면 다른 서버에 붙은 사용자에게 이벤트가 가지 않는다는 한계를 재현한 뒤, Redis Pub/Sub으로 잇습니다.

<!-- diagram: scale-out -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart LR
    UA["사용자 A"] --> Nginx["nginx<br/>round-robin · WebSocket upgrade"]
    UB["사용자 B"] --> Nginx
    Nginx --> S1["api-1<br/>A의 세션"]
    Nginx --> S2["api-2<br/>B의 세션"]
    S1 -->|"publish"| Redis[("Redis<br/>채널: chat-events")]
    Redis -->|"subscribe"| S1
    Redis -->|"subscribe"| S2
    S1 -.->|"로컬 세션에만 전송"| UA
    S2 -.->|"로컬 세션에만 전송"| UB
    S1 --> DB[("PostgreSQL")]
    S2 --> DB
```

Pub/Sub은 저장하지 않습니다. 구독이 끊긴 동안의 이벤트는 사라지므로, 재연결한 화면은 DB에서 다시 조회해 메워야 합니다. 그 한계까지 문서에 남깁니다.

---

## 11. 회의실 예약과 동시성

같은 회의실의 시간이 겹치는 예약은 허용하지 않습니다. 구간은 `[start, end)`로 해석하고, 겹침은 `기존 시작 < 새 종료 AND 기존 종료 > 새 시작`으로 판정합니다.

<!-- diagram: reservation -->
```mermaid
---
config:
  markdownAutoWrap: false
  sequence:
    actorMargin: 40
    messageMargin: 44
    mirrorActors: false
    wrap: false
---
sequenceDiagram
    participant R1 as 요청 1 (14:00~15:00)
    participant R2 as 요청 2 (14:30~15:30)
    participant API as API
    participant DB as PostgreSQL

    par 동시 요청
        R1->>API: 예약
        R2->>API: 예약
    end
    API->>DB: 회의실 행 잠금 (SELECT ... FOR UPDATE)
    Note over API,DB: 요청 1이 먼저 잠금을 얻음
    API->>DB: 겹치는 예약 조회 → 없음
    API->>DB: 요청 1 저장 · commit
    API-->>R1: 성공
    API->>DB: 요청 2 잠금 획득 → 겹침 조회 → 1건
    API-->>R2: 실패 (시간이 겹칩니다)
```

`@Transactional`만으로는 직렬화되지 않는다는 것을 동시 요청 100건으로 재현하고, 비관적 락·낙관적 락·DB 제약을 같은 조건에서 비교해 중복 0건을 확인하는 것이 이 단계의 목표입니다.

---

## 12. 관측성

운영 지표(서버 상태)와 서비스 통계(관리자 대시보드)를 분리합니다. 서버 상태는 Grafana에서 보고, 서비스 활동은 앱 안의 관리자 화면에서 봅니다.

<!-- diagram: observability -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart LR
    subgraph App["Spring Boot"]
        Act["Actuator"]
        Mic["Micrometer<br/>HTTP · JVM · HikariCP<br/>Redis · WebSocket · LLM"]
    end
    Prom["Prometheus<br/>스크레이프 · 저장"]
    Graf["Grafana<br/>대시보드 · 알림"]
    Load["k6 부하 테스트"]

    Mic --> Act -->|"/actuator/prometheus"| Prom --> Graf
    Load -->|"로그인 · 메시지 · 동시 예약"| App
    Load -.->|"p95 · p99 · 에러율 비교"| Graf
```

측정하지 않은 수치는 적지 않습니다. 실험은 같은 환경·데이터·부하 조건에서 Before와 After를 비교하고, 한계까지 기록합니다.

---

## 13. AI 대화 요약

채팅방 참여자만 자기 방의 메시지 범위를 요약할 수 있습니다. 외부 LLM이 느리거나 실패해도 채팅 자체는 영향을 받지 않도록 격리합니다.

<!-- diagram: ai-summary -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart LR
    Req["요약 요청<br/>(방 · 메시지 범위)"] --> Auth{"참여자인가"}
    Auth -->|"아니오"| Deny["403"]
    Auth -->|"예"| Cache{"같은 범위<br/>캐시 있음"}
    Cache -->|"예"| Done["요약 반환"]
    Cache -->|"아니오"| Fetch["DB에서 메시지 조회"]
    Fetch --> Call["LLM 호출<br/>Timeout · 제한된 Retry"]
    Call -->|"성공"| Save["결과 저장 · 캐시"] --> Done
    Call -->|"실패 · 429 · 타임아웃"| Fallback["Fallback 안내<br/>채팅 기능은 정상"]
    Rate["Rate Limit<br/>사용자 · 방 단위"] -.-> Call
```

API 키는 서버 환경변수에만 둡니다. 외부로 나가는 대화 내용은 테스트 데이터 기준으로 다루고, 비용과 토큰 수를 지표로 남깁니다.

---

## 14. 패키지 구조

도메인별 패키지 안에 컨트롤러·서비스·도메인·리포지토리를 함께 둡니다. 공통 응답·예외·보안은 `common`, 설정은 `config`에 모읍니다.

<!-- diagram: packages -->
```mermaid
---
config:
  markdownAutoWrap: false
  flowchart:
    curve: basis
    padding: 14
    nodeSpacing: 36
    rankSpacing: 48
    wrappingWidth: 800
---
flowchart TB
    Root["com.jhwjhw.naverworkscloneapi"]
    Root --> Common["common<br/>ApiResponse · 예외 핸들러 · JWT 필터"]
    Root --> Config["config<br/>Security · WebSocket · CORS"]
    Root --> Identity["identity<br/>회사 · 부서 · 구성원 · 계정"]
    Root --> Auth["auth<br/>로그인 · 토큰 · 내 정보"]
    Root --> Address["addressbook<br/>조직도 · 검색 · 즐겨찾기"]
    Root --> Chat["chat<br/>방 · 메시지 · 리액션 · 공지<br/>실시간 이벤트 발행"]
    Root --> Notification["notification<br/>알림 생성 · 조회 · 읽음"]
    Root --> Presence["presence<br/>접속 상태"]
    Root --> Schedule["schedule<br/>일정 · 회의실 · 예약"]
    Root --> Dashboard["dashboard<br/>Today · 관리자 통계"]
    Root --> AI["ai<br/>대화 요약"]
    Chat -.->|"ApplicationEvent"| Notification
    Chat -.->|"AFTER_COMMIT"| Presence
```

서비스는 ApplicationEvent로 느슨하게 연결합니다. 메시지 서비스가 알림 서비스를 직접 호출하지 않고 이벤트를 던지면, 알림·실시간 발행이 각자 구독해 처리합니다.

---

## 15. 개발 로드맵

짧은 수직 슬라이스(화면 ↔ API ↔ DB ↔ 테스트) 단위로 한 단계를 끝내고 다음으로 갑니다.

<!-- diagram: roadmap -->
```mermaid
---
config:
  markdownAutoWrap: false
---
timeline
    title Phase별 개발 순서
    section 기반
        Phase 1 : Spring Boot · PostgreSQL · JWT 로그인
        Phase 2 : RBAC · USER/ADMIN 데모 전환
        Phase 3 : 회사 · 부서 · 구성원 · 조직도
    section 메신저
        Phase 4 : 1:1 · 그룹방 · 메시지 · STOMP · 읽음
        Phase 5 : 답장 · 수정 · 삭제 · 전달 · 공지 · 리액션 · 알림
        Phase 6 : Redis Presence TTL
        Phase 7 : 서버 2대 · nginx · Redis Pub/Sub
    section 일정 · 홈
        Phase 8 : 일정 · 회의실 · 동시성 제어
        Phase 9 : Today · 관리자 대시보드
    section 고도화
        Phase 10 : 성능 분석 · Prometheus · Grafana
        Phase 11 : AI 대화 요약
        Phase 12 : OAuth2 · 감사 로그 · CI/CD · 공개 배포
```

---

## 기술 스택

| 분류 | 선택 | 이유 |
| --- | --- | --- |
| 언어 · 프레임워크 | Java 21 · Spring Boot 4.1 · Spring MVC | LTS 기반, REST · Security · JPA · WebSocket을 한 생태계에서 |
| 인증 | Spring Security · JWT (Access 3h · Refresh 7d 회전) | Stateless API와 서버 중심 인가 |
| 영속성 | Spring Data JPA · Hibernate · Flyway | 도메인 모델과 스키마 이력을 코드와 함께 관리 |
| 데이터베이스 | PostgreSQL (Supabase) | 관계형 정합성과 관리형 무료 환경 |
| 실시간 | WebSocket · STOMP · Redis Pub/Sub | 양방향 이벤트와 서버 간 전파 |
| 접속 상태 | Redis TTL · Heartbeat | 비정상 종료에도 자동 정리 |
| 테스트 | JUnit 5 · Spring Boot Test · H2 | 도메인 규칙을 통합 테스트로 고정 |
| 관측 | Actuator · Micrometer · Prometheus · Grafana · k6 | 측정 근거 기반 개선 |
| 배포 | Docker Compose · nginx · Oracle Cloud · GitHub Actions · GHCR | 비용 0원 상시 가동 |

## 로컬 실행

```powershell
# src/main/resources/application-local.yml.example 을 복사해 application-local.yml 작성 (Git 제외)
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run

# 테스트
.\mvnw.cmd test
```

DB 비밀번호와 JWT 시크릿은 환경변수나 로컬 설정 파일에만 둡니다. 테스트 프로필은 H2를 씁니다.

## 문서

- [프론트엔드 README](https://github.com/wjsgusdn12/naverworks_clone_front#readme)
- 이 README의 다이어그램은 `scripts/build-diagrams.mjs`가 `docs/diagrams/`에 SVG로 뽑아 두며, 프론트 앱의 "프로젝트 소개" 모달이 같은 파일을 보여 줍니다.
