import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const apiPrefix = __ENV.API_PREFIX || '/api';
const eventId = __ENV.EVENT_ID;
const vus = Number(__ENV.VUS || 100);
const maxDuration = __ENV.MAX_DURATION || '1m';

if (!eventId) {
  throw new Error('EVENT_ID is required.');
}

export const orderSuccess = new Counter('order_success');
export const soldOut = new Counter('sold_out');
export const businessError = new Counter('business_error');
export const unexpectedError = new Counter('unexpected_error');
export const admissionRejected = new Counter('admission_rejected');
export const idempotencyConflict = new Counter('idempotency_conflict');
export const idempotencyInProgress = new Counter('idempotency_in_progress');

export const options = {
  scenarios: {
    correctness_burst: {
      executor: 'shared-iterations',
      vus,
      iterations: vus,
      maxDuration,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const unique = `${__VU}-${__ITER}-${Date.now()}`;
  const idempotencyKey = `ticket-order-${unique}`;
  const res = http.post(
    `${baseUrl}${apiPrefix}/events/${eventId}/ticket-orders`,
    JSON.stringify({
      quantity: 1,
      paymentMethod: 'CARD',
      buyer: {
        name: `Load Test ${unique}`,
        email: `ticket-${unique}@load-test.local`,
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
        scenario: 'ticket_order_correctness',
      },
    },
  );

  classify(res);

  check(res, {
    'classified response': () => true,
  });
}

function classify(res) {
  let body;
  try {
    body = res.json();
  } catch (error) {
    unexpectedError.add(1);
    return;
  }

  if (res.status === 200 && body && body.code === '200') {
    orderSuccess.add(1);
    return;
  }

  if (body && body.code === 'TICKET_409_001') {
    soldOut.add(1);
    return;
  }

  if (body && body.code === 'TICKET_429_001') {
    admissionRejected.add(1);
    return;
  }

  if (body && body.code === 'TICKET_409_002') {
    idempotencyConflict.add(1);
    return;
  }

  if (body && body.code === 'TICKET_409_003') {
    idempotencyInProgress.add(1);
    return;
  }

  if (res.status >= 400 && res.status < 500 && body && body.code) {
    businessError.add(1);
    return;
  }

  unexpectedError.add(1);
}
