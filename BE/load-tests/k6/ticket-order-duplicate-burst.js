import http from 'k6/http';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 409, 429));

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const apiPrefix = __ENV.API_PREFIX || '/api';
const eventId = __ENV.EVENT_ID;
const idempotencyKey = __ENV.IDEMPOTENCY_KEY;
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || 100);

if (!eventId) {
  throw new Error('EVENT_ID is required.');
}

if (!idempotencyKey) {
  throw new Error('IDEMPOTENCY_KEY is required.');
}

export const orderSuccess = new Counter('order_success');
export const idempotencyInProgress = new Counter('idempotency_in_progress');
export const idempotencyConflict = new Counter('idempotency_conflict');
export const admissionRejected = new Counter('admission_rejected');
export const unexpectedError = new Counter('unexpected_error');
export const timeoutError = new Counter('timeout_error');

export const options = {
  scenarios: {
    duplicate_burst: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
  thresholds: {
    idempotency_conflict: ['count==0'],
    unexpected_error: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const requestBody = JSON.stringify({
  quantity: 1,
  paymentMethod: 'CARD',
  buyer: {
    name: 'Duplicate Burst Buyer',
    email: 'duplicate-burst@example.local',
    phone: '01012345678',
  },
});

export default function () {
  const res = http.post(
    `${baseUrl}${apiPrefix}/events/${eventId}/ticket-orders`,
    requestBody,
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      timeout: __ENV.REQ_TIMEOUT || '10s',
      tags: {
        scenario: 'ticket_order_duplicate_burst',
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

  if (res.status >= 200 && res.status < 300 && errorCode === '200') {
    orderSuccess.add(1);
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
