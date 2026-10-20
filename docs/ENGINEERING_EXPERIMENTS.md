# ENGINEERING_EXPERIMENTS — 구현 이후 트러블슈팅·성능 재현 명세

> 이 문서는 앞으로 수행할 **실험 후보**다. 현재 문제를 실제 겪었거나 해결했다는 의미가 아니다.  
> 반드시 `PROJECT_CONTEXT.md`의 현재 요구사항과 `PROGRESS.md` 실제 상태를 확인한다.

## 모든 실험의 공통 규칙

1. 실험 가설(무엇이 느려지거나 깨지는가)을 명시한다.
2. 환경(CPU/메모리/DB 버전, 요청 조건/데이터 수/동시 사용자)을 기록한다.
3. 데이터가 필요하면 통제 가능한 **더미 데이터**를 생성한다.
4. Before 원시 로그, SQL/EXPLAIN, 테스트 결과, Grafana 스크린샷/지표를 확보한다.
5. 대안을 **같은 조건**에서 비교하고 하나를 선택한 이유를 기록한다.
6. After를 실제 측정하고 표에 기록한다. 예시 수치를 실제처럼 작성하지 않는다.
7. 실패 케이스·한계·추후 개선 가능성을 기록한다.
8. 서버/Redis/LLM 무료 티어의 사용량을 고려하고 무리한 부하를 가하지 않는다.

## Case 01 — JPA 영속성, N+1, SQL
- 도메인 예시: 채팅방 20개, 사용자/최근 메시지 조회.
- 쿼리 카운트와 실제 실행 SQL 측정.
- LAZY 접근 시 추가 쿼리 수, Fetch Join/DTO projection/Batch 전략 비교.
- 컬렉션 페이징/카테시안 곱/멀티 컬렉션 join 위험도 기록.
- 별도 테스트: Dirty Checking, flush != commit, `@Transactional` 프록시 self invocation.
- 지표: SELECT 수, 전체 latency, DB load.

## Case 02 — 메시지 조회 대량 데이터
- 예: room별 10만~100만 이상 메시지(테스트 머신 처리 가능량으로 결정).
- `LIMIT 50 OFFSET ...` vs `id < :cursor ORDER BY id DESC LIMIT 50`.
- `(room_id,id)` 복합 인덱스 선택 이유와 실제 EXPLAIN 확인.
- 각 조건 응답시간/p95/p99, 페이지 유실·중복, 정렬/커서 경계 검증.

## Case 03 — WebSocket 다중 서버
- A 접속 서버 #1, B 접속 서버 #2 강제 배치.
- 로컬 simple broker만 쓰면 발생하는 전달 누락 여부 관찰.
- Redis Pub/Sub 이벤트 relay 적용 후 교차 서버 전달 확인.
- 양 서버 구독 시 중복 브로드캐스트, DB commit 후 publish, subscriber disconnect 시 유실 검토.
- 서버 한 대 재시작 후 재연결/기록 동기화 테스트.

## Case 04 — Presence TTL & 다중 탭
- 정상 disconnect, 탭 강제 종료, Wi-Fi/네트워크 끊김, 서버 프로세스 강제 종료.
- Heartbeat TTL 갱신/만료 시 ONLINE 잔존 최대 지연 측정.
- 같은 USER가 탭 2개 켜고 1개만 닫을 때 ONLINE 유지.
- 오래된 연결의 disconnect로 새 연결 상태가 사라지는 경쟁 조건 테스트.
- Redis 명령/TTL 비용 체크.

## Case 05 — 회의실 예약 원자성
- 동일 room에서 14:00~15:00 100개 동시 예약(예시 규모).
- 겹치는 구간 14:30~15:30, 인접 15:00~16:00, start=end 오류도 검증.
- 단순 `exists() -> save()` Race Condition 재현.
- MySQL/PG 선택 DB의 제약조건/락 전략 검토. Simple UNIQUE(start_at)는 부족.
- optimistic, pessimistic, 필요 시 distributed lock을 각각 실험.
- 최종 DB 무결성/중복 0건, 성공 실패 건수, throughput, p95, lock wait.

## Case 06 — HikariCP / Thread / 외부 지연
- 호출 증가에 따른 p95/p99, JVM Thread, Pool Active/Pending, DB slow query 비교.
- 병목 원인이 Pool 한도인지 SQL인지 외부 서비스인지 측정 없이 추정하지 않는다.
- Timeout, 처리 시간 최적화, Pool 조정 등을 비교하되 설정값 무작정 늘리는 것을 해결로 삼지 않는다.

## Case 07 — 관리자 대시보드 집계
- Raw 접속/활동 이벤트(방문 수/활성 사용자/온라인 구분) 월/일/시간 조회.
- DISTINCT COUNT, 기간 범위, 인덱스, 집계 테이블 방식 비교.
- timezone, 00시 경계, 7일 범위 중복/고유 사용자 정의를 테스트.

## Case 08 — 외부 LLM API 안정성
- 외부 LLM에 인위적 지연/429/5xx/타임아웃을 주입.
- timeout, 제한된 retry+backoff, fallback, 요청 제한, 중복 요약 cache 적용 전후.
- async/queue를 쓴다면 bounded queue/실패한 작업 재처리 정책 확인.
- p95, failure rate, 총 외부 요청 수, 토큰/비용 비교. 채팅 기능 장애 격리 확인.

## Case 09 — API 인가 / 데이터 격리
- 인증 없는 API → 401(또는 로그인 응답), USER 관리자 API → 403.
- 임의 `companyId`, `roomId`, `memberId` 변경으로 다른 조직/방의 데이터 조회/수정 차단.
- WS CONNECT/SUBSCRIBE/SEND 역시 검사.
- 데모 계정 변경 시 실제 개인 계정이 관리자 권한을 획득하지 않음.

## Case 10 — 모니터링 / 배포 장애
- API RPS·p95/p99·Error Rate, JVM/GC, HikariCP, Redis/WS 연결수, LLM 호출 지표 구성.
- 애플리케이션 #1 또는 Nginx를 내렸을 때 서비스 가용성의 실제 한계 관찰.
- Prometheus/Grafana/Actuator 외부 접근권한 및 metric label 과다 카디널리티 검토.
- '앱 인스턴스 2개'와 '서버/네트워크 수준 HA'를 구별.

## 출력 예시 (숫자는 처음에는 TBD로 둔다)

| Case | 환경/데이터 | Before | After | 판단 | 증거 링크 |
|---|---|---|---|---|---|
| N+1 | TBD | TBD | TBD | TBD | TBD |
| Cursor | TBD | TBD | TBD | TBD | TBD |
| Reservation | TBD | TBD | TBD | TBD | TBD |
| WebSocket | TBD | TBD | TBD | TBD | TBD |
| LLM | TBD | TBD | TBD | TBD | TBD |
