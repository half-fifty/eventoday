# Eventoday

행사(박람회·전시회) 주최자와 부스 참가자를 위한 행사 운영 플랫폼입니다. 부스 모집·신청, 평면도, 티켓 구매/결제, 입장 QR, 부스 예약 등을 지원합니다.

## 기술 스택

**Backend (`BE/`)**
- Java 21, Spring Boot 4.1.0
- Spring Data JPA, Flyway, PostgreSQL
- Spring Data Redis
- Spring Security, OAuth2 Client
- Gradle

**Frontend (`FE/`)**
- React 18, Vite 5
- react-router-dom
- Tailwind CSS

**Infra**
- Docker Compose (PostgreSQL 17, Redis 7)

## 프로젝트 구조

```
BE/     Spring Boot 백엔드 (DDD 도메인별 패키지 구조)
FE/     React 프론트엔드
docs/   요구사항/API/테이블 명세 등 프로젝트 문서
```

## 로컬 개발 환경 설정

### 사전 요구사항
- JDK 21
- Node.js 20+
- Docker / Docker Compose

### 1. 인프라 실행 (PostgreSQL, Redis)

```bash
docker compose up -d
```

기본 접속 정보는 `.env.example`을 참고하세요. 필요 시 `.env`로 복사해 값을 덮어쓸 수 있습니다.

### 2. 백엔드 실행

```bash
cd BE
./gradlew bootRun
```

DB 마이그레이션(Flyway)은 애플리케이션 기동 시 자동으로 적용됩니다.

### 3. 프론트엔드 실행

```bash
cd FE
npm install
npm run dev
```

## 브랜치 / PR 규칙

- 기본 브랜치: `main`
- PR은 `.github/pull_request_template.md` 양식을 따릅니다.
- 이슈는 `.github/ISSUE_TEMPLATE/` 의 유형별 템플릿을 사용합니다.
- PR 생성 시 `pr-check.yml` CI(백엔드 빌드/테스트, 프론트엔드 빌드)가 자동 실행됩니다.
