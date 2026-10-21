# PROGRESS — 네이버웍스 클론 실제 개발 현황

> 갱신: 2026-10-20 / 확인 수준: **소스·Supabase PostgreSQL 연결 확인 기준**
> **설계 문서의 미래 기능을 구현 완료로 표시하지 말 것.**

## 1. 현재 보고된 사실

- VS Code 작업 폴더 `JHWJHW` 내부에 **독립 GitHub 저장소 2개**가 연결됨:
  - `naverworks_clone_api` (Backend: Spring Boot)
  - `naverworks_clone_front` (Frontend: Next.js + TypeScript)
- **로그인 화면 UI 구현됨** (정확한 소스 파일 경로 확인 필요).
- **홈 화면 UI 구현됨** (정확한 소스 파일 경로 확인 필요).
- 임시로 **프론트에서 `admin / 1234` 입력을 관리자처럼 취급하고 홈으로 이동**하게 만듦.
- 네이버/카카오/구글 **소셜 로그인 버튼 UI만** 만들어짐.
- Spring Boot가 Supabase PostgreSQL에 연결되고 Flyway V1·V2 스키마가 적용됨.
- 실제 로그인 API와 프론트 연동은 아직 진행 전이며, 프론트 목업 인증을 유지 중.
- JWT 액세스·리프레시 토큰 인증 API와 최소 Spring Security 인가를 구현함. 성공·실패 JSON은 `ApiResponse<T>` 형식으로 통일했으며 OAuth2 로그인은 미구현.
- 주소록·조직 기본 조회와 구성원 즐겨찾기, 검색 기록 기능은 구현되어 프론트와 연동됨.
- 메신저 MVP REST 기반(1:1/그룹방, 메시지 전송·커서 조회·읽음 위치·읽음 멤버 조회)은 구현됨. 실시간 WebSocket/STOMP, Redis Presence/PubSub, 첨부·이모지·수정·삭제·답장 기능은 아직 미구현.
- 예약/통계/모니터링/AI 기능은 **구현됐다고 확인할 수 없음**.

## 2. 기술 선택 대기

- Java/Spring Boot 실제 버전(사용자는 학습 시 Boot 3 선호)
- Gradle/Maven 및 현재 빌드 상태
- MySQL vs PostgreSQL (Supabase면 PG)
- 세션 쿠키 기반 로그인 vs JWT
- User/Company/Role 초기 ERD
- API 요청/응답/오류 계약
- 데모 USER/ADMIN 인증 전환 방법

## 3. 지금 에이전트가 해야 할 첫 작업: Phase 0 (읽기 전용)

1. 두 저장소에서 현 소스/README/AGENTS/설정/브랜치/변경내역 확인.
2. 로그인 UI/홈 UI/소셜 버튼/임시 admin 인증의 실제 코드 위치와 동작 확인.
3. API·DB·Security·테스트 존재 여부 확인.
4. 미구현 범위와 설계 대비 차이를 구분해 **브리핑만** 제공.
5. Phase 1(로그인 API 연결)의 최소 작업 순서, API 계약, 스키마, 파일·테스트 계획 제출.
6. **사용자가 `진행해`라고 승인할 때까지 코드/파일을 수정하지 않음**.

## 4. 다음 구현: Phase 1 (승인 후)

**기존 로그인/홈 UI를 보존하고 실제 인증 연동**

```text
Next.js 로그인 UI
  → Spring Boot 로그인 API
  → RDBMS 사용자 조회·비밀번호 확인
  → 서버 인증/인가
  → 로그인 상태 유지
  → 기존 홈 페이지
  → 로그아웃
```

- 로그인 실패/미인증/새로고침 테스트.
- 프론트 하드코딩 admin 분기 제거.
- 세 OAuth 버튼은 당장은 UI만 유지하면서 미지원 기능임을 명시.
- 코드 푸시/커밋은 별도 명시적 승인 전 금지.

## 5. 완료/미완료 정리 방식

| 항목 | 상태 | 근거 |
|---|---|---|
| 두 저장소 생성 및 동일 VS Code 열기 | 사용자 보고 완료 | 코드/원격 추가 확인 필요 |
| 로그인 UI | 사용자 보고 완료 | 파일 검사 전 |
| 홈 UI | 사용자 보고 완료 | 파일 검사 전 |
| 임시 `admin / 1234` 화면 전환 | 사용자 보고 완료 | 보안 인증 아님, 제거 예정 |
| 네이버/카카오/구글 버튼 | UI만 | OAuth2 X |
| 로그인 API | 구현 | JWT 액세스·리프레시 토큰 발급/갱신/로그아웃/내 정보 API |
| 프론트 로그인 연동 | 미구현 | 기존 목업 인증 유지 |
| RDBMS 연결 | 완료 | Supabase PostgreSQL + Flyway V1·V2 적용 확인 |
| Security USER/ADMIN 실제 인가 | 최소 구현 | JWT role 클레임을 `ROLE_*` 권한으로 변환 |
| Messenger REST MVP | 구현 | 채팅방·메시지·읽음 위치·Cursor Pagination API |
| Messenger 실시간/확장 기능 | 미구현 | WebSocket/STOMP, Redis, 첨부·수정·삭제·답장 등 |
| AI/배포 | 계획 | 구현 여부 소스 조사 전 |

## 6. 매 작업 종료 시 기록 템플릿

### 2026-10-21 / 주소록 조직도 다중 회사 조회
- 사용자 요청: 본인 즐겨찾기 허용, 모든 회사 조직도 조회, 조직도·회사·부서 펼치기/접기.
- 결정 사항: 조직도는 모든 회사를 반환하고, 주소록 목록은 선택 회사 기준으로 조회한다. 채팅방의 회사 간 접근 제한은 유지한다.
- API/DB/Front 계약 변화: `GET /api/v1/addressBook/organization`은 회사 배열을 반환하며, `GET /api/v1/addressBook/member`는 선택 `companyId`를 받는다. `member_favorite`의 본인 추가 금지 제약은 Flyway V15에서 제거한다.
- 실제 수정 파일: API의 주소록 서비스·컨트롤러·응답 DTO·회사 Repository·V15, Front의 주소록 API 타입·주소록 화면.
- 검증: API `./mvnw.cmd -q -DskipTests compile` 성공, Front `npm run lint`, `npm run build` 성공.
- Git Commit/Push: 수행하지 않음.

### 2026-10-22 / 다중 회사 즐겨찾기·메시지
- 사용자 요청: 다른 회사 구성원도 즐겨찾기와 메시지에 연동.
- 결정 사항: 즐겨찾기는 개인 목록으로 회사 필터 없이 조회한다. 1:1·그룹 채팅방은 다른 회사 활성 구성원도 참가할 수 있다.
- 보안 규칙: 채팅방 메시지·조회·관리 권한은 회사가 아니라 활성 채팅방 참가자 여부로 검증한다. 주소록 그룹은 기존처럼 같은 회사 범위를 유지한다.
- 검증: API `./mvnw.cmd -q -DskipTests compile` 성공.
- Git Commit/Push: 수행하지 않음.

### 2026-10-22 / 새 메시지방 정렬
- 사용자 요청: 새 메시지방도 대화 시작 전 목록 최상단에 표시.
- 결정 사항: 채팅방 목록은 마지막 메시지 시각이 있으면 그 시각, 없으면 생성 시각을 기준으로 최신순 정렬한다.
- Front 반영: 새 방 생성 직후 해당 방을 선택한 뒤 목록을 갱신한다.
- 검증: API `./mvnw.cmd -q -DskipTests compile` 성공, Front `npm run lint`, `npm run build` 성공.
- Git Commit/Push: 수행하지 않음.

### YYYY-MM-DD / Phase N
- 사용자 요청:
- 사용자 승인 여부:
- 현 코드에서 확인한 사실:
- 결정 사항과 이유:
- API/DB/Front 계약 변화:
- 실제 수정 파일(저장소명 포함):
- 실행한 명령·빌드·테스트 및 실제 결과:
- 보안/회귀/미해결 문제:
- 다음 가장 작은 단계:
- Git Commit/Push: 별도 요청 여부:
