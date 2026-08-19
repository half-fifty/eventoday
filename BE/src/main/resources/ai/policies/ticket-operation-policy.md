# Ticket Operation Policy

## TICKET_ORDER_STATUS_GUIDE

TicketOrder status is managed by Java domain state.
PENDING_PAYMENT means the ticket order is waiting for payment completion or deposit flow completion.
CONFIRMED means payment finalization confirmed the ticket order.
EXPIRED means the pending ticket order expired and inventory release logic has run.
REFUNDED means Java refund finalization changed the ticket order after an allowed refund.
Current status for a specific order must come from the read-only operation tool.

## PAYMENT_ORDER_STATUS_GUIDE

PaymentOrder status is managed by Java payment state.
PENDING means the payment order is created but not completed.
WAITING_FOR_DEPOSIT means virtual account payment is waiting for deposit.
PAID means Java payment finalization completed the payment order.
EXPIRED means Java expiration processing expired the payment order.
REFUNDED means Java refund finalization marked the paid order as refunded.
Current payment order status must come from backend query results.

## EXCHANGE_CODE_ISSUANCE_AFTER_PAYMENT

PaymentFinalizer confirms a ticket order when payment finalization succeeds.
For event ticket orders, it calls TicketExchangeCodeIssuer.issueIfAbsent after marking the payment order paid and confirming the ticket order.
TicketExchangeCodeIssuer creates one exchange code for each ticket quantity if codes do not already exist.
If existing exchange code count does not match ticket quantity, Java treats the data as inconsistent.

## ADMISSION_TICKET_STATUS_GUIDE

AdmissionTicket status is managed by Java domain state.
ISSUED means the ticket was issued and may be check-in eligible if Java AdmissionEligibilityPolicy also accepts the event and related exchange code.
USED means check-in has already been processed.
CANCELLED means the admission ticket is not currently issued for check-in.
EXPIRED means the admission ticket is not currently issued for check-in.
Current admission ticket status must come from the read-only operation tool.

## QR_AVAILABILITY_GUIDE

AdmissionTicketOperationQueryService reports qrAvailable only when the admission ticket status is ISSUED and the server has a QR token for the ticket.
The raw QR token is secret and must not be shown to operators through AI.
When a QR token is submitted to the server, the server resolves it to an admissionTicketId before AI tool use.
Do not use policy text to infer a QR token value.

## CHECK_IN_ELIGIBILITY_SOURCE_OF_TRUTH

AdmissionEligibilityPolicy is the source of truth for check-in eligibility.
It checks event match, event published status, event end time, already used ticket status, and whether the ticket is ISSUED.
The policy returns eligible and reasonCode, and AI must not change those values.
For a specific ticket, use getAdmissionEligibility and follow the Java result even when policy context appears more general.
