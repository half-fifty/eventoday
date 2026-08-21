import assert from "node:assert/strict";
import test from "node:test";

import {
  admissionRetryDelayMs,
  isTicketOrderAdmissionRejected,
  MAX_ADMISSION_RETRIES,
} from "./ticketOrderAdmissionRetry.js";

const withFixedRandom = (value, callback) => {
  const originalRandom = Math.random;
  Math.random = () => value;
  try {
    callback();
  } finally {
    Math.random = originalRandom;
  }
};

test("only ticket-order admission 429 is retryable", () => {
  assert.equal(MAX_ADMISSION_RETRIES, 2);
  assert.equal(isTicketOrderAdmissionRejected({ status: 429, code: "TICKET_429_001" }), true);
  assert.equal(isTicketOrderAdmissionRejected({ status: 409, code: "TICKET_409_002" }), false);
  assert.equal(isTicketOrderAdmissionRejected({ status: 409, code: "TICKET_409_003" }), false);
  assert.equal(isTicketOrderAdmissionRejected({ status: 409, code: "TICKET_409_001" }), false);
  assert.equal(isTicketOrderAdmissionRejected({ status: 500, code: "UNKNOWN_ERROR" }), false);
});

test("uses numeric Retry-After seconds with jitter", () => {
  withFixedRandom(0, () => {
    assert.equal(
      admissionRetryDelayMs({ headers: new Headers({ "Retry-After": "1" }) }, 1),
      1000
    );
  });

  withFixedRandom(0.999, () => {
    assert.equal(
      admissionRetryDelayMs({ headers: new Headers({ "Retry-After": "1" }) }, 1),
      1500
    );
  });
});

test("falls back to bounded attempt-based backoff when Retry-After is missing or invalid", () => {
  withFixedRandom(0, () => {
    assert.equal(admissionRetryDelayMs({ headers: new Headers() }, 1), 1000);
    assert.equal(admissionRetryDelayMs({ headers: new Headers() }, 2), 2000);
    assert.equal(
      admissionRetryDelayMs({ headers: new Headers({ "Retry-After": "soon" }) }, 1),
      1000
    );
  });
});
