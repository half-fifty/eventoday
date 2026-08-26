package com.min.edu.common.mail;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import jakarta.mail.MessagingException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final MailSenderProperties mailSenderProperties;

    @Override
    public void send(EmailMessage email) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(
                    mailSenderProperties.getFrom(),
                    mailSenderProperties.getFromName()
            );
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.content(), true);

            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            log.warn(
                "SMTP email send failed. category={}, exceptionType={}, recipientDomain={}, fromConfigured={}",
                categorize(exception),
                exception.getClass().getName(),
                recipientDomain(email.to()),
                mailSenderProperties.getFrom() != null && !mailSenderProperties.getFrom().isBlank()
            );
            throw new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED, exception);
        }
    }

    private String categorize(Exception exception) {
        if (hasCause(exception, AuthenticationFailedException.class)
                || hasCause(exception, MailAuthenticationException.class)) {
            return "AUTHENTICATION";
        }
        if (hasCause(exception, SocketTimeoutException.class)) {
            return "TIMEOUT";
        }
        if (hasCause(exception, ConnectException.class)) {
            return "CONNECTIVITY";
        }
        if (exception instanceof MessagingException
                || exception instanceof UnsupportedEncodingException) {
            return "MESSAGE_BUILDING";
        }
        if (exception instanceof MailSendException) {
            return "MAIL_SEND";
        }
        return "UNKNOWN";
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String recipientDomain(String recipient) {
        if (recipient == null) {
            return "unknown";
        }
        int atIndex = recipient.lastIndexOf('@');
        if (atIndex < 0 || atIndex == recipient.length() - 1) {
            return "unknown";
        }
        return recipient.substring(atIndex + 1);
    }
}
