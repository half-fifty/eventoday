package com.min.edu.common.mail;

public record EmailMessage(
        String to,
        String subject,
        String content
) {
}
