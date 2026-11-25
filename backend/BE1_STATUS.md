# WITH WON BE-1 백엔드 현황

최종 갱신: 2026-11-25

## 담당 범위

BE-1은 고객 백엔드와 공통 인증을 담당한다.

- 인증: 고객 로그인, 직원 로그인, 세션 확인, 로그아웃
- 고객: 고객 사건 조회, 사건 상세, 추가 자료 제출, 알림 등 고객용 API
- 고객 소유 데이터: `customers`, `auth_sessions`, `accounts`, `transactions`, `bank_security_events`, `incidents`, `incident_transactions`, `applications`, `application_form_versions`, `application_consents`, `evidence_files`, `evidence_versions`, `notifications` 및 인증에 필요한 `employees`

BE-2의 직원 업무 처리, AI 분석, 검토, 환급·보상 의사결정 API는 이 브랜치의 작업 범위에 포함하지 않는다. 공유 DB 마이그레이션에 BE-2 테이블이 포함되는 것은 전체 스키마 초기화에 필요하기 때문이며, BE-2 기능을 구현한다는 뜻은 아니다.

## 브랜치 및 커밋

- `chore/be-infra-bootstrap`: JPA, MySQL, Flyway, 최신 27개 테이블 V1 스키마
- `feature/be-auth-customer-login`: 고객 로그인 구현
- `feature/be-auth-employee-login`: 직원 로그인 구현
- `feature/be-auth-session-check`: 현재 작업 중인 세션 확인 구현
- `feature/be-auth-logout`: 로그아웃 구현
- `feature/be-customer/incident-domain`: 고객 사건 도메인 매핑
- `feature/be-customer/evidence-domain`: 고객 증빙 도메인 매핑
- `feature/be-auth/api-contract-alignment`: 엑셀 API 명세 기준 인증 계약 정렬
- `feature/be-customer/incident-list`: CUS-02 고객 사건 목록 API
- `feature/be-customer/incident-detail`: CUS-03 고객 사건 상세 API

기능별로 브랜치를 분리하고, 테스트가 끝난 뒤 기능 단위로 커밋한다. 현재 세션 확인 브랜치의 변경사항은 아직 커밋하지 않았다.

## 완료된 작업

### 인프라 및 DB

- Spring Boot 4.1.3, Java 21, Gradle 9.9.2 기반 확인
- JPA와 Spring Security 의존성 추가
- MySQL Connector와 Flyway MySQL 지원 추가
- `ddl-auto=validate`, Flyway `classpath:db/migration` 설정
- 최신 Customer/Incident 명칭 기준 27개 테이블, 309개 컬럼, 63개 FK의 [V1__create_schema.sql](src/main/resources/db/migration/V1__create_schema.sql) 작성
- 운영 비밀번호·JWT secret은 저장소에 기록하지 않고 환경 변수로 주입

### 인증

- 고객 로그인: `POST /api/v1/auth/customer/login`
- 직원 로그인: `POST /api/v1/auth/employee/login`
- BCrypt 비밀번호 검증
- HS256 JWT 발급
- JWT 원문은 저장하지 않고 SHA-256 해시만 `auth_sessions.token_hash`에 저장
- 고객·직원 인증 서비스와 단위 테스트 작성
- API 산출물 기준 직원 로그인 요청 필드를 `employeeNumber`에서 `loginId`로 정렬

API 명세 원본은 `(우리WON) 프로젝트 산출물 양식 (4).xlsx`의 `+ API명세서` 시트에서 확인했다. `AUTH-03`의 `profile` 상세 구조와 유휴 만료 응답 정책은 추가 계약 확인 후 반영한다.

## 현재 작업: AUTH-03 세션 확인

`feature/be-auth-session-check`에서 다음을 구현했다.

- JWT 서명, claims, 만료 시각 검증
- Bearer 토큰을 `auth_sessions`와 대조
- 폐기·만료·위조 토큰 차단
- 유효 요청의 `last_activity_at` 갱신
- Spring Security 인증 객체 연결
- `GET /api/v1/auth/session` 응답: `userId`, `role`
- 직원 로그인 경로를 명세에 맞게 `/api/v1/auth/employee/login`으로 정리

### AUTH-04 로그아웃

현재 `feature/be-auth-logout`에서 다음을 구현했다.

- `POST /api/v1/auth/logout` 추가
- 현재 Bearer 토큰의 세션을 `revoked_at`으로 폐기
- 폐기된 토큰의 재사용 차단
- 고객·직원 공통 로그아웃 처리

### 고객 사건 도메인 기반

현재 `feature/be-customer-incident-domain`에서 API 명세와 독립적인 DB 도메인 기반을 준비했다.

- 최신 `incidents` 테이블을 `Incident` 엔티티로 매핑
- 고객(`customer_id`)과 담당 직원(`assigned_employee_id`) 관계 매핑
- 고객별 최신 사건 목록 조회 Repository 추가
- 고객별 사건 단건 조회 Repository 추가
- 고객 ID를 조건으로 강제하는 사건 조회 서비스 추가
- 상태값은 API 계약 확정 전까지 문자열로 유지

### 고객 증빙 도메인 기반

현재 `feature/be-customer-evidence-domain`에서 다음을 준비했다.

- `evidence_files`와 `evidence_versions` 엔티티 매핑
- 파일과 버전의 관계 매핑
- 고객 ID를 포함한 증빙 파일·버전 Repository 조회
- 고객 소유권을 검증하는 증빙 조회 서비스 추가
- 추출·마스킹 JSON 컬럼은 DB 구조 보존을 위해 문자열 JSON으로 매핑
- AI 재시도·JSON Schema 검증 로직은 BE-2 범위로 구현하지 않음

### CUS-02 고객 사건 목록

현재 `feature/be-customer/incident-list`에서 명세 기준 목록 API를 구현했다.

- `GET /api/v1/customer/incidents`
- CUSTOMER 역할만 접근 가능
- 인증된 고객 ID로 본인 사건만 조회
- `status`, `page`, `size` query 지원
- `content`, `page`, `size`, `totalElements`, `totalPages` 응답
- 페이지 크기 1~100 제한

### CUS-03 고객 사건 상세

현재 `feature/be-customer/incident-detail`에서 현재 도메인 범위의 상세 API를 구현했다.

- `GET /api/v1/customer/incidents/{incidentId}`
- CUSTOMER 역할 및 고객 소유권 검증
- 사건 핵심 정보와 고객 기본 프로필 반환
- 사건에 연결된 증빙 파일 목록 반환
- 미구현 집계 영역(신청, 동의, 보완, 변경, 결과)은 임의 응답으로 만들지 않고 후속 도메인 작업으로 분리

## 검증 현황

- 고객 로그인, 직원 로그인, JWT 발급 단위 테스트는 통과했다.
- AUTH-03 변경 후 컴파일 및 신규 테스트를 추가로 실행해야 한다.
- 전체 애플리케이션 테스트는 DB 연결과 `WITHWON_JWT_SECRET` 환경 변수가 필요하다.
- RDS는 이전 실행에서 TCP 3306 연결 시간 초과가 발생해 Flyway를 실제 적용하지 못했다. 보안 그룹, VPN 또는 접속 IP 허용 후 별도로 마이그레이션을 검증해야 한다.

## 다음 순서

1. 공통 인증 예외 응답과 인증 필터 동작 정리
2. API 명세 확정 후 고객 사건 목록 조회 API 구현
3. 고객 사건 상세 조회 API 구현
4. 고객 계정·사건 도메인 엔티티와 Repository 보강
5. DB 접속 가능 시 Flyway V1 적용 및 `validate` 확인

## 미결정 사항

- JWT 절대 만료 및 재발급 정책
- 세션 idle 만료를 `expires_at`에 반영하는 방식
- 인증 실패 공통 응답 포맷
- 고객 계정 allowlist와 초기 계정 데이터 공급 방식
- `check_items` seed 및 일부 nullable/제약조건 정책

## 작업 원칙

- API 명세와 최신 27개 테이블 설계를 기준으로 한다.
- 기존 팀원의 업무 코드를 수정하거나 BE-2 기능을 선행 구현하지 않는다.
- 기능별 브랜치 생성 → 집중 테스트 → 기능별 커밋 순서로 진행한다.
- DB 비밀번호와 JWT secret은 코드·문서·커밋에 남기지 않는다.
