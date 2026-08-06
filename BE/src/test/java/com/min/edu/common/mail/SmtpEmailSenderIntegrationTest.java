package com.min.edu.common.mail;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailSenderIntegrationTest {

    @Test
    void gmailSmtp로_메일을_발송한다() throws IOException {
        Map<String, String> environment = loadEnvironment();
        String username = requireValue(environment, "SMTP_USERNAME");
        String password = requireValue(environment, "SMTP_PASSWORD");

        JavaMailSenderImpl javaMailSender = createJavaMailSender(username, password);

        MailSenderProperties mailSenderProperties = new MailSenderProperties();
        mailSenderProperties.setFrom(username);
        mailSenderProperties.setFromName("EVENTODAY");

        EmailSender emailSender = new SmtpEmailSender(javaMailSender, mailSenderProperties);
        EmailMessage message = new EmailMessage(
                username,
                "[EVENTODAY] QR 교환 코드 안내",
                """
                <div style="max-width: 560px; margin: 0 auto; padding: 32px;
                            font-family: Arial, sans-serif; color: #222222;">
                    <h1 style="margin: 0 0 24px; color: #6750a4;">EVENTODAY</h1>
                    <h2 style="margin: 0 0 12px;">QR 교환 코드가 발급되었습니다.</h2>
                    <p style="margin: 0; line-height: 1.6; color: #555555;">
                        행사 입장 시 아래 교환 코드를 제시해 주세요.
                    </p>

                    <div style="margin: 24px 0; padding: 24px; border: 2px solid #6750a4;
                                border-radius: 12px; background-color: #f7f4ff; text-align: center;">
                        <p style="margin: 0 0 10px; color: #666666; font-size: 13px;">
                            QR 교환 코드
                        </p>
                        <strong style="color: #6750a4; font-size: 28px; letter-spacing: 5px;">
                            TEST-1234
                        </strong>
                    </div>

                    <div style="text-align: center;">
                        <a href="https://github.com/half-fifty/eventoday"
                           style="display: inline-block; padding: 13px 26px;
                                  border-radius: 8px; background-color: #6750a4;
                                  color: #ffffff; font-weight: bold; text-decoration: none;">
                            EVENTODAY 확인하기
                        </a>
                    </div>

                    <p style="margin-top: 32px; color: #999999; font-size: 12px;">
                        본 메일은 EVENTODAY에서 자동 발송되었습니다.
                    </p>
                </div>
                """
        );

        assertDoesNotThrow(() -> emailSender.send(message));
    }

    private JavaMailSenderImpl createJavaMailSender(String username, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");

        return mailSender;
    }

    private Map<String, String> loadEnvironment() throws IOException {
        Map<String, String> environment = new HashMap<>(System.getenv());
        Path envFile = Path.of("..", ".env");

        if (!Files.exists(envFile)) {
            return environment;
        }

        for (String line : Files.readAllLines(envFile)) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || !trimmedLine.contains("=")) {
                continue;
            }

            String[] entry = trimmedLine.split("=", 2);
            environment.putIfAbsent(entry[0].trim(), entry[1].trim());
        }

        return environment;
    }

    private String requireValue(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
