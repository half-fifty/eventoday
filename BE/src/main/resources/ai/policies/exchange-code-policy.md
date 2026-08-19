# Exchange Code Policy

## EXCHANGE_CODE_STATUS_GUIDE

ExchangeCode status is managed by Java domain state.
ISSUED means the code has been created and may be redeemable if the redemption service also accepts the event, holder, expiry, and duplicate-ticket checks.
REDEEMED means the code has already been used to issue an admission ticket.
CANCELLED means the code has been cancelled and is not redeemable.
EXPIRED means the code is not valid for redemption.
Current status for a specific exchange code must come from the read-only operation tool, not from this document.

## EXCHANGE_CODE_REDEMPTION_REQUIREMENTS

ExchangeCodeRedemptionService only redeems an exchange code when the code is in ISSUED state.
If expiresAt is present, it must still be after the current server time.
The related event must be PUBLISHED and its endAt must still be after the current server time.
Holder and source checks are performed by Java service logic.
This document describes the rule source, but Java service results are the final decision.

## EXCHANGE_CODE_REDEMPTION_ISSUES_ADMISSION_TICKET

Successful redemption changes the exchange code to REDEEMED and creates an AdmissionTicket by calling AdmissionTicket.issue.
The issued admission ticket starts in ISSUED status and receives a server-generated QR token.
The QR token itself must not be exposed in AI answers or prompts.
If an admission ticket already exists for the exchange code, redemption is blocked by the Java service.

## EXCHANGE_CODE_GUEST_ORDER_REDEMPTION

Guest order redemption is allowed only through the Java guest order access validation flow.
The exchange code must belong to the validated ticket order and must not already have a holder member.
The same ISSUED status, expiry, event status, and event end checks still apply.
The order access token is a secret and must never be exposed or embedded.

## EXCHANGE_CODE_REFUND_RELATION

RefundEligibilityPolicy treats an already redeemed exchange code as a refund blocking reason.
When Java evaluates exchangeCodeRedeemed as true, the refund reasonCode is EXCHANGE_CODE_ALREADY_REDEEMED.
Do not infer refund availability from this document alone.
Use Java refund eligibility results or read-only tool results when current resource state is involved.
