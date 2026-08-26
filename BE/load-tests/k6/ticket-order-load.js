import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 429));

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const apiPrefix = __ENV.API_PREFIX || '/api';
const eventIds = (__ENV.EVENT_IDS || '')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);
const rate = Number(__ENV.RATE || 50);
const duration = __ENV.DURATION || '30s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.MAX_VUS || 200);

if (eventIds.length === 0) {
  throw new Error('EVENT_IDS is required. Example: EVENT_IDS=123 or EVENT_IDS=123,124,125');
}

export const orderSuccess = new Counter('order_success');
export const soldOut = new Counter('sold_out');
export const businessError = new Counter('business_error');
export const unexpectedError = new Counter('unexpected_error');
export const timeoutError = new Counter('timeout_error');
export const admissionRejected = new Counter('admission_rejected');
export const idempotencyConflict = new Counter('idempotency_conflict');
export const idempotencyInProgress = new Counter('idempotency_in_progress');

export const options = {
  scenarios: {
    ticket_order_arrival_rate: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const iteration = exec.scenario.iterationInTest;
  const eventId = eventIds[iteration % eventIds.length];
  const unique = `${iteration}-${__VU}-${Date.now()}`;
  const idempotencyKey = `ticket-order-${unique}`;

  const res = http.post(
    `${baseUrl}${apiPrefix}/events/${eventId}/ticket-orders`,
    JSON.stringify({
      quantity: 1,
      paymentMethod: 'CARD',
      buyer: {
        name: `Load Test ${unique}`,
        email: `ticket-order-${unique}@load-test.local`,
        phone: '01012345678',
      },
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      timeout: __ENV.REQ_TIMEOUT || '10s',
      tags: {
        scenario: 'ticket_order_load',
      },
    },
  );

  classify(res);
}

function classify(res) {
  if (isRequestTimeout(res)) {
    timeoutError.add(1);
    return;
  }

  let body;
  try {
    body = res.json();
  } catch (error) {
    unexpectedError.add(1);
    return;
  }

  const errorCode = body && body.code;

  if (res.status === 429 && errorCode === 'TICKET_429_001') {
    admissionRejected.add(1);
    return;
  }

  if (errorCode === 'TICKET_409_002') {
    idempotencyConflict.add(1);
    return;
  }

  if (errorCode === 'TICKET_409_003') {
    idempotencyInProgress.add(1);
    return;
  }

  if (res.status === 200 && errorCode === '200') {
    orderSuccess.add(1);
    return;
  }

  if (errorCode === 'TICKET_409_001') {
    soldOut.add(1);
    return;
  }

  if (res.status >= 400 && res.status < 500 && errorCode) {
    businessError.add(1);
    return;
  }

  unexpectedError.add(1);
}

function isRequestTimeout(res) {
  if (res.status !== 0) {
    return false;
  }

  const error = String(res.error || '').toLowerCase();
  return error.includes('timeout')
    || error.includes('timed out')
    || error.includes('deadline exceeded');
}
