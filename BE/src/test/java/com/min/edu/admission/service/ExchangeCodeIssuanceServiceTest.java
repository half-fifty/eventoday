package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.member.domain.PlatformRole;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ExchangeCodeIssuanceServiceTest {

    @Mock
    private ExchangeCodeIssuanceFinalizer issuanceFinalizer;

    @Mock
    private ExchangeCodeRequestEmailRecorder emailRecorder;

    @Mock
    private ExchangeCodeEmailSendLease emailSendLease;

    @Mock
    private EmailSender emailSender;

    private ExchangeCodeIssuanceService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeCodeIssuanceService(
            issuanceFinalizer,
            emailRecorder,
            emailSendLease,
            emailSender
        );
        org.mockito.Mockito.lenient().when(emailSendLease.tryClaim(any()))
            .thenReturn(ExchangeCodeEmailSendLeaseClaim.acquired("token"));
    }

    @Test
    void issue_sendsEmailAndReturnsResponseWithoutCodes() {
        ExchangeCodeIssuanceResult result = result(
            "행사 <테스트> & \"EVENT\"",
            "ABCDEF-123456-7890AB"
        );
        OffsetDateTime emailedAt = OffsetDateTime.now();
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(emailedAt);

        ExchangeCodeRequestDtos.IssuanceResponse response = service.issue(
            7L,
            admin()
        );

        assertThat(response.requestId()).isEqualTo(7L);
        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ExchangeCodeRequestStatus.ISSUED);
        assertThat(response.requestedQuantity()).isEqualTo(1);
        assertThat(response.generatedQuantity()).isEqualTo(1);
        assertThat(response.emailedAt()).isEqualTo(emailedAt);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("requester@example.com");
        assertThat(captor.getValue().subject())
            .isEqualTo("[EVENTODAY] 외부 판매용 입장 코드 발급 완료");
        assertThat(captor.getValue().content()).contains("ABCDEF-123456-7890AB");
        assertThat(captor.getValue().content())
            .contains("행사 &lt;테스트&gt; &amp; &quot;EVENT&quot;");
        verify(emailRecorder).markEmailed(7L);
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void issue_failsWhenUnauthenticatedBeforeLookup() {
        assertThatThrownBy(() -> service.issue(999L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verify(issuanceFinalizer, never()).issueOrPrepareEmail(any());
    }

    @Test
    void issue_failsWhenActorIsNotAdmin() {
        assertThatThrownBy(() -> service.issue(
            7L,
            new AuthenticatedMemberDto(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        verify(issuanceFinalizer, never()).issueOrPrepareEmail(any());
    }

    @Test
    void issue_doesNotMarkEmailedWhenEmailSendingFails() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED))
            .given(emailSender)
            .send(any(EmailMessage.class));

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void issue_keepsEmailUnmarkedAcrossRepeatedSmtpFailures() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED))
            .given(emailSender)
            .send(any(EmailMessage.class));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.issue(7L, admin()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);
        }

        verify(issuanceFinalizer, org.mockito.Mockito.times(5)).issueOrPrepareEmail(7L);
        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease, org.mockito.Mockito.times(5)).release(7L, "token");
    }

    @Test
    void issue_reusesExistingCodesWhenPreviousEmailAttemptFailed() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        OffsetDateTime emailedAt = OffsetDateTime.now();
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(emailedAt);

        ExchangeCodeRequestDtos.IssuanceResponse response = service.issue(7L, admin());

        assertThat(response.emailedAt()).isEqualTo(emailedAt);
        verify(issuanceFinalizer).issueOrPrepareEmail(7L);
        verify(emailSender).send(any(EmailMessage.class));
    }

    @Test
    void issue_releasesLeaseAfterSmtpFailureSoRetryCanSendAgain() {
        FailsFirstEmailSender retryEmailSender = new FailsFirstEmailSender();
        ExchangeCodeIssuanceService retryService = new ExchangeCodeIssuanceService(
            issuanceFinalizer,
            emailRecorder,
            new InMemoryLease(),
            retryEmailSender
        );
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        OffsetDateTime emailedAt = OffsetDateTime.now();
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(emailedAt);

        assertThatThrownBy(() -> retryService.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        ExchangeCodeRequestDtos.IssuanceResponse response = retryService.issue(7L, admin());

        assertThat(response.emailedAt()).isEqualTo(emailedAt);
        assertThat(retryEmailSender.sendCount()).isEqualTo(2);
        verify(issuanceFinalizer, org.mockito.Mockito.times(2)).issueOrPrepareEmail(7L);
        verify(emailRecorder).markEmailed(7L);
    }

    @Test
    void issue_doesNotSendEmailWhenLeaseIsAlreadyHeld() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailSendLease.tryClaim(7L))
            .willReturn(ExchangeCodeEmailSendLeaseClaim.alreadyInFlight());

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        verify(emailSender, never()).send(any());
        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease, never()).release(any(), any());
    }

    @Test
    void issue_doesNotSendEmailWhenLeaseStoreIsUnavailable() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailSendLease.tryClaim(7L))
            .willReturn(ExchangeCodeEmailSendLeaseClaim.unavailable());

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        verify(emailSender, never()).send(any());
        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease, never()).release(any(), any());
    }

    @Test
    void issue_doesNotOpenOuterTransactionSoIssuanceCanCommitBeforeEmail() throws Exception {
        Method issue = ExchangeCodeIssuanceService.class.getMethod(
            "issue",
            Long.class,
            AuthenticatedMemberDto.class
        );
        Method resendEmail = ExchangeCodeIssuanceService.class.getMethod(
            "resendEmail",
            Long.class,
            AuthenticatedMemberDto.class
        );

        assertThat(issue.getAnnotation(Transactional.class)).isNull();
        assertThat(resendEmail.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void issue_propagatesRecorderFailureAfterEmailSent() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE))
            .given(emailRecorder)
            .markEmailed(7L);

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);

        verify(emailSender).send(any(EmailMessage.class));
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void resendEmail_sendsExistingCodesAndReturnsResponseWithoutCodes() {
        ExchangeCodeIssuanceResult result = result(
            "행사 <테스트> & \"EVENT\"",
            "ABCDEF-123456-7890AB"
        );
        OffsetDateTime emailedAt = OffsetDateTime.now();
        given(issuanceFinalizer.prepareEmailResend(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(emailedAt);

        ExchangeCodeRequestDtos.EmailResendResponse response = service.resendEmail(
            7L,
            admin()
        );

        assertThat(response.requestId()).isEqualTo(7L);
        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ExchangeCodeRequestStatus.ISSUED);
        assertThat(response.requestedQuantity()).isEqualTo(1);
        assertThat(response.codeCount()).isEqualTo(1);
        assertThat(response.emailedAt()).isEqualTo(emailedAt);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("requester@example.com");
        assertThat(captor.getValue().content()).contains("ABCDEF-123456-7890AB");
        assertThat(captor.getValue().content())
            .contains("행사 &lt;테스트&gt; &amp; &quot;EVENT&quot;");
        verify(emailRecorder).markEmailed(7L);
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void resendEmail_failsWhenUnauthenticatedBeforeLookup() {
        assertThatThrownBy(() -> service.resendEmail(999L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verify(issuanceFinalizer, never()).prepareEmailResend(any());
    }

    @Test
    void resendEmail_failsWhenActorIsNotAdmin() {
        assertThatThrownBy(() -> service.resendEmail(
            7L,
            new AuthenticatedMemberDto(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        verify(issuanceFinalizer, never()).prepareEmailResend(any());
    }

    @Test
    void resendEmail_doesNotMarkEmailedWhenEmailSendingFails() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.prepareEmailResend(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED))
            .given(emailSender)
            .send(any(EmailMessage.class));

        assertThatThrownBy(() -> service.resendEmail(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void resendEmail_propagatesAlreadySentConflictBeforeSendingEmail() {
        given(issuanceFinalizer.prepareEmailResend(7L))
            .willThrow(new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT));

        assertThatThrownBy(() -> service.resendEmail(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT);

        verify(emailSender, never()).send(any());
        verify(emailRecorder, never()).markEmailed(any());
        verify(emailSendLease, never()).tryClaim(any());
    }

    @Test
    void resendEmail_propagatesRecorderFailureAfterEmailSent() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.prepareEmailResend(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE))
            .given(emailRecorder)
            .markEmailed(7L);

        assertThatThrownBy(() -> service.resendEmail(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);

        verify(emailSender).send(any(EmailMessage.class));
        verify(emailSendLease).release(7L, "token");
    }

    @Test
    void concurrentIssueOnlyOneRequestStartsSmtpSend() throws Exception {
        BlockingEmailSender blockingEmailSender = new BlockingEmailSender();
        ExchangeCodeIssuanceService concurrentService = new ExchangeCodeIssuanceService(
            issuanceFinalizer,
            emailRecorder,
            new InMemoryLease(),
            blockingEmailSender
        );
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issueOrPrepareEmail(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(OffsetDateTime.now());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executorService.submit(() -> callIssue(concurrentService));
            assertThat(blockingEmailSender.awaitStarted()).isTrue();
            Future<Boolean> second = executorService.submit(() -> callIssue(concurrentService));

            TimeUnit.MILLISECONDS.sleep(100);
            assertThat(blockingEmailSender.sendCount()).isEqualTo(1);
            blockingEmailSender.complete();

            assertThat(first.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(1, TimeUnit.SECONDS)).isFalse();
            assertThat(blockingEmailSender.sendCount()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void concurrentResendOnlyOneRequestStartsSmtpSend() throws Exception {
        BlockingEmailSender blockingEmailSender = new BlockingEmailSender();
        ExchangeCodeIssuanceService concurrentService = new ExchangeCodeIssuanceService(
            issuanceFinalizer,
            emailRecorder,
            new InMemoryLease(),
            blockingEmailSender
        );
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.prepareEmailResend(7L)).willReturn(result);
        given(emailRecorder.markEmailed(7L)).willReturn(OffsetDateTime.now());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executorService.submit(() -> callResend(concurrentService));
            assertThat(blockingEmailSender.awaitStarted()).isTrue();
            Future<Boolean> second = executorService.submit(() -> callResend(concurrentService));

            TimeUnit.MILLISECONDS.sleep(100);
            assertThat(blockingEmailSender.sendCount()).isEqualTo(1);
            blockingEmailSender.complete();

            assertThat(first.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(1, TimeUnit.SECONDS)).isFalse();
            assertThat(blockingEmailSender.sendCount()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private ExchangeCodeIssuanceResult result(String eventName, String code) {
        return new ExchangeCodeIssuanceResult(
            7L,
            1L,
            eventName,
            1,
            1,
            "requester@example.com",
            List.of(code),
            OffsetDateTime.parse("2026-08-07T10:00:00+09:00"),
            ExchangeCodeRequestStatus.ISSUED,
            null
        );
    }

    private AuthenticatedMemberDto admin() {
        return new AuthenticatedMemberDto(99L, PlatformRole.PLATFORM_ADMIN);
    }

    private boolean callIssue(ExchangeCodeIssuanceService service) {
        try {
            service.issue(7L, admin());
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);
            return false;
        }
    }

    private boolean callResend(ExchangeCodeIssuanceService service) {
        try {
            service.resendEmail(7L, admin());
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);
            return false;
        }
    }

    private static class InMemoryLease extends ExchangeCodeEmailSendLease {
        private final AtomicBoolean held = new AtomicBoolean();

        InMemoryLease() {
            super(null, java.time.Duration.ofSeconds(30));
        }

        @Override
        public ExchangeCodeEmailSendLeaseClaim tryClaim(Long requestId) {
            return held.compareAndSet(false, true)
                ? ExchangeCodeEmailSendLeaseClaim.acquired("token")
                : ExchangeCodeEmailSendLeaseClaim.alreadyInFlight();
        }

        @Override
        public void release(Long requestId, String token) {
            held.set(false);
        }
    }

    private static class BlockingEmailSender implements EmailSender {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch complete = new CountDownLatch(1);
        private final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public void send(EmailMessage message) {
            sendCount.incrementAndGet();
            started.countDown();
            try {
                if (!complete.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to complete email send.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to complete email send.", exception);
            }
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(1, TimeUnit.SECONDS);
        }

        void complete() {
            complete.countDown();
        }

        int sendCount() {
            return sendCount.get();
        }
    }

    private static class FailsFirstEmailSender implements EmailSender {
        private final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public void send(EmailMessage message) {
            if (sendCount.incrementAndGet() == 1) {
                throw new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED);
            }
        }

        int sendCount() {
            return sendCount.get();
        }
    }
}
