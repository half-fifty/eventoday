package com.min.edu.common.mail;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

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
            throw new BusinessException(GlobalErrorCode.EMAIL_SEND_FAILED, exception);
        }
    }
}
