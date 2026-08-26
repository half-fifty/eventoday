export const MAX_ADMISSION_RETRIES = 2;
export const ADMISSION_RETRY_JITTER_MS = 500;
export const ADMISSION_RETRY_FALLBACK_BASE_MS = 1000;
export const TICKET_ORDER_ADMISSION_REJECTED_CODE = "TICKET_429_001";

export const isTicketOrderAdmissionRejected = (error) =>
  error?.status === 429 && error?.code === TICKET_ORDER_ADMISSION_REJECTED_CODE;

export const admissionRetryDelayMs = (error, retryAttempt) => {
  const retryAfter = error?.headers?.get?.("Retry-After");
  const retryAfterSeconds = retryAfter === null || retryAfter === undefined || retryAfter === ""
    ? Number.NaN
    : Number(retryAfter);
  const baseDelay = Number.isFinite(retryAfterSeconds) && retryAfterSeconds >= 0
    ? retryAfterSeconds * 1000
    : retryAttempt * ADMISSION_RETRY_FALLBACK_BASE_MS;

  return baseDelay + Math.floor(Math.random() * (ADMISSION_RETRY_JITTER_MS + 1));
};
