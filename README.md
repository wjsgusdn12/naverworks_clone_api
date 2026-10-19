# Naver Works Clone API

네이버웍스의 협업 경험을 참고해 만드는 개인 포트폴리오용 기업 협업 플랫폼의 백엔드 저장소입니다.
실제 네이버웍스의 내부 구현이나 디자인을 복제하는 프로젝트는 아닙니다.

> 현재 상태: 개발 준비 단계. 아래 기술과 기능은 계획이며, 구현 완료를 의미하지 않습니다.

## 목표

Spring Boot 기반 단일 서버 MVP를 먼저 구현하고, 실제 문제를 재현·측정·개선하는 과정을 기록합니다.

- 인증·인가와 회사 단위 데이터 격리
- 실시간 메신저와 WebSocket 메시지 처리
- 일정 및 회의실 예약의 동시성 제어
- 조회 성능, 관측 가능성, 외부 API 안정성 검증

## 핵심 기능 범위

1. 조직, 구성원, 인증 및 역할 기반 권한 관리
2. 1:1·그룹 채팅, 메시지 저장, 읽음 상태, 실시간 전송
3. 일정 관리 및 회의실 시간 구간 예약

후속 범위로 관리자 대시보드, Redis 기반 Presence, 다중 인스턴스 메시지 전달, 운영 모니터링, 채팅 요약 기능을 검토합니다.

## 예정 기술 스택

| 영역 | 계획 |
| --- | --- |
| Language / Framework | Java, Spring Boot 3.x, Spring MVC, Spring Security |
| Persistence | Spring Data JPA, Hibernate, 필요 시 QueryDSL |
| Database | MySQL 또는 PostgreSQL 중 선택 예정 |
| Real-time | Spring WebSocket, STOMP |
| Shared state | Redis TTL, Pub/Sub |
| Test | JUnit 5, Spring Boot Test, Testcontainers |
| Monitoring | Actuator, Micrometer, Prometheus, Grafana |
| Infrastructure | Docker, Nginx, Spring Boot 다중 인스턴스 |

## 설계·검증 주제

- REST와 WebSocket 모두에서 서버 측 멤버십·권한 검증
- Cursor Pagination과 인덱스를 이용한 메시지 조회 성능 비교
- Redis TTL/Heartbeat 기반 온라인 상태와 다중 접속 경쟁 조건
- 회의실 예약 시간 구간의 중복 방지와 락 전략 비교
- 메시지 영속화와 Redis Pub/Sub 이벤트 전달의 실패·중복 상황 검토

## 개발 로드맵

1. 프로젝트 환경 구성, 조직·회원·인증·인가
2. 단일 서버 메신저 MVP
3. 메시지 고급 기능과 파일 첨부
4. Redis Presence 및 다중 인스턴스 메시지 전달
5. 일정·회의실 예약과 동시성 제어
6. 성능 테스트, 모니터링, 외부 LLM 기반 채팅 요약

## 실행 방법

프로젝트 초기화 후 Java 버전, 빌드 도구, 데이터베이스 및 실행 방법을 이 문서에 추가합니다.
