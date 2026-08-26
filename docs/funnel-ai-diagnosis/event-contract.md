# 퍼널 기반 사용자 행동 분석 + AI 진단 — 이벤트 계약

배경/정책은 `requirements.md`, 저장소/집계 정책은 `technical-design.md` 참고.

## 이벤트 타입 카탈로그

| action_type | 발행 주체 | 트리거 | 퍼널 단계 | 비고 |
|---|---|---|---|---|
| (없음, 세션 존재 자체) | - | 세션의 첫 액션 도착 | 1. 방문 | 별도 이벤트 타입 없이 세션 시작 = 방문 도달로 판정 (`technical-design.md` 참고) |
| `view_event_detail` | FE | 행사상세 페이지 로드 | 2. 행사상세 조회 | 세션의 첫 액션인 경우가 많음 |
| `open_purchase_modal` | FE | "티켓 구매하기" 버튼 클릭, 구매 모달 오픈 | 3. 티켓선택 | 페이지 이동 아님, 모달 오픈 이벤트 |
| `complete_payment` | **결제 도메인 Outbox** (FE 아님) | 결제 성공 확정 | 4. 결제완료 | 클라이언트 신호 신뢰 안 함. 아래 "결제완료 이벤트" 참고 |
| `view_booth_list` | FE | 부스 목록 페이지(`/events/:eventId/ongoing`) 로드 | 핵심 퍼널 아님, 보조 | `FunnelSession.is_booth_explored` 계산용 |
| `back_navigation` | FE | 브라우저 뒤로가기(`popstate`) 감지 | 핵심 퍼널 아님, 보조 | 이탈 판정 트리거 아님, 이탈 원인 설명 보조 신호 |
| `page_close` | FE (`navigator.sendBeacon`) | `beforeunload`/`visibilitychange` 감지 | 핵심 퍼널 아님, 보조 | 일반 fetch로는 전송 유실 가능 — sendBeacon 필수 |

퍼널 도달/전환 계산 시 사용하는 건 `view_event_detail` → `open_purchase_modal` → `complete_payment`
3단계 + 세션 존재(방문) 뿐이다. 나머지(`view_booth_list`, `back_navigation`, `page_close`)는 비교/보조
분석용으로만 쓰인다.

(참고: 결제 시도 실패와 미시도를 구분하는 `payment_failed` 이벤트는 검토했다가 보류했다. 나중에 필요하면
`FunnelSession.is_payment_failed` 같은 플래그로 다시 추가할 수 있다.)

## FE → BE: 이벤트 수집 API

### `POST /api/funnel-actions`

인증: 비로그인 허용. 로그인 상태면 JWT에서 `user_id`를 추출해 같이 기록한다.
`anonymous_id`는 요청 쿠키에서 읽고, 쿠키가 없으면 서버가 발급해 `Set-Cookie`로 응답에 포함시킨다.

**Request**
```json
{
  "sessionId": "uuid",
  "eventId": "long",
  "actionType": "view_event_detail",
  "occurredAt": "2026-08-18T14:32:01+09:00",
  "properties": {}
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId | string(uuid) | Y | FE가 생성/관리하는 세션 식별자 |
| eventId | long | Y | 어느 행사에 대한 액션인지 |
| actionType | string | Y | 위 카탈로그의 값 중 하나 (`complete_payment` 제외 — 이건 FE가 직접 보내지 않음) |
| occurredAt | ISO8601 | Y | 클라이언트 참고용 발생 시각. **집계/배치 컷오프의 기준으로 쓰지 않는다** (아래 참고) |
| properties | object | N | 액션별 부가 정보. `back_navigation`의 `fromPage` 정도만 실제로 쓰고, 나머지 액션은
  대부분 세션 단위 계산만으로 충분해 비워둔다 |

**occurredAt vs receivedAt**: 클라이언트 시계는 부정확하거나 조작될 수 있어 신뢰하지 않는다.
서버는 요청을 받은 시각을 `receivedAt`으로 별도 기록하고, **세션 귀속일·배치 컷오프 등 모든 집계 판단은
`receivedAt` 기준으로만 한다.** `occurredAt`은 참고용(디버깅, 클라이언트-서버 시간차 관찰)으로만 보존한다.

**Response**:
- 정상: `202 Accepted`, 본문 없음. (Kafka 발행까지만 확인, ES 색인 완료를 기다리지 않음)
- `sessionId`/`actionType` 형식이 잘못된 경우: `400 Bad Request`
- 존재하지 않는 `eventId`인 경우: 사용자 경험에 영향을 주면 안 되는 분석용 API이므로, 에러를 내지 않고
  `202`로 받은 뒤 Consumer 단에서 조용히 버린다 (검증 실패로 인한 FE 흐름 방해를 피하기 위함)

### 기존 API 변경사항

`POST /api/tickets/purchase` 요청 body에 `sessionId` 필드를 추가해야 한다. 이 값이 있어야 결제 도메인이
`complete_payment` 이벤트를 발행할 때 원래 세션과 이어붙일 수 있다. (아래 "결제 도메인과 사전 합의 필요
사항" 참고 — 이 변경은 결제 도메인 담당 파트와 함께 진행해야 한다.)

## Kafka 계약

### 토픽

| 토픽명 | 발행 주체 | 구독 주체 | 용도 |
|---|---|---|---|
| `funnel-actions` | `FunnelActionController`(BE) | `FunnelActionConsumer` | `complete_payment`를 제외한 모든 사용자 행동 이벤트 |
| `payment-completed-events` (제안, 결제 도메인과 협의 필요) | 결제 도메인 Outbox Publisher | `FunnelActionConsumer` | 결제 성공이 확정된 사실. `funnel-analytics`는 이 토픽도 함께 구독 |

두 토픽으로 나누는 이유: 결제 도메인이 자신의 Outbox에서 발행하는 메시지 스키마/발행 시점을 `funnel-analytics`가
강제하지 않기 위함이다. 결제 도메인은 자기 도메인 이벤트를 자기 이름의 토픽으로 발행하고,
`funnel-analytics`는 소비자 입장에서 구독만 한다.

### 메시지 스키마 (공통)

```json
{
  "actionId": "uuid",
  "sessionId": "uuid",
  "eventId": "long",
  "anonymousId": "uuid",
  "userId": "long | null",
  "actionType": "string",
  "occurredAt": "2026-08-18T14:32:01+09:00",
  "receivedAt": "2026-08-18T14:32:01.482+09:00",
  "properties": {}
}
```

`receivedAt`은 `FunnelActionController`가 요청을 수신한 시각으로, 서버가 채워 넣는다 (클라이언트가
보내는 값이 아니다). `FunnelAction`을 세션에 귀속시키거나 배치 컷오프를 판단할 때는 항상 이 값을 쓴다.

- **파티션 키**: `sessionId`. 같은 세션의 이벤트가 같은 파티션에 들어가야 소비 순서가 보장된다.
- **멱등성**: `actionId`는 발행 주체가 생성하는 고유값(UUID). `FunnelActionConsumer`는 이미 색인된
  `actionId`를 다시 받으면 덮어쓰기만 하고 중복 카운트하지 않는다 (Kafka는 최소 1회 전달을 보장하므로
  같은 메시지를 두 번 받을 수 있다는 전제).

## 결제 도메인과 사전 합의 필요 사항

아래 항목은 `funnel-analytics` 쪽에서 정할 수 없고, 결제 담당 파트와 실제로 확인해야 한다.

- [ ] `payment-completed-events` 토픽명/메시지 스키마를 위 제안대로 갈지, 다른 형태로 갈지
- [ ] 발행 메시지에 `sessionId`가 포함되는지 — `POST /api/tickets/purchase` 요청에 실은 `sessionId`를
      결제 도메인이 Outbox 메시지까지 그대로 전달해줄 수 있는지
- [ ] Outbox 저장과 결제 성공 트랜잭션이 커밋되는 시점과, 실제로 Kafka에 발행되는 시점 사이의 지연이
      어느 정도인지 (일일 배치의 3시간 버퍼가 충분한지 판단 근거)
- [ ] `paymentId` 같은 결제 도메인 고유 식별자를 메시지에 함께 실어줄 수 있는지 (시스템 로그와의 상관
      조회 시 결제 실패율을 정확히 연결하는 데 필요)
