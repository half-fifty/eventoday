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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeCodeIssuanceServiceTest {

    @Mock
    private ExchangeCodeIssuanceFinalizer issuanceFinalizer;

    @Mock
    private ExchangeCodeRequestEmailRecorder emailRecorder;

    @Mock
    private EmailSender emailSender;

    private ExchangeCodeIssuanceService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeCodeIssuanceService(
            issuanceFinalizer,
            emailRecorder,
            emailSender
        );
    }

    @Test
    void issue_sendsEmailAndReturnsResponseWithoutCodes() {
        ExchangeCodeIssuanceResult result = result(
            "행사 <테스트> & \"EVENT\"",
            "ABCDEF-123456-7890AB"
        );
        OffsetDateTime emailedAt = OffsetDateTime.now();
        given(issuanceFinalizer.issue(7L)).willReturn(result);
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
    }

    @Test
    void issue_failsWhenUnauthenticatedBeforeLookup() {
        assertThatThrownBy(() -> service.issue(999L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verify(issuanceFinalizer, never()).issue(any());
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

        verify(issuanceFinalizer, never()).issue(any());
    }

    @Test
    void issue_doesNotMarkEmailedWhenEmailSendingFails() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issue(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED))
            .given(emailSender)
            .send(any(EmailMessage.class));

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EMAIL_SEND_FAILED);

        verify(emailRecorder, never()).markEmailed(any());
    }

    @Test
    void issue_propagatesRecorderFailureAfterEmailSent() {
        ExchangeCodeIssuanceResult result = result("event", "ABCDEF-123456-7890AB");
        given(issuanceFinalizer.issue(7L)).willReturn(result);
        org.mockito.BDDMockito.willThrow(new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE))
            .given(emailRecorder)
            .markEmailed(7L);

        assertThatThrownBy(() -> service.issue(7L, admin()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);

        verify(emailSender).send(any(EmailMessage.class));
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
}
