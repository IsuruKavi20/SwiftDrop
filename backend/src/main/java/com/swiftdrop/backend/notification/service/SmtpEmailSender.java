package com.swiftdrop.backend.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String configuredUsername;

    private static final String DEFAULT_FROM = "notifications@swiftdrop.me";

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String subject, String bodyHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            String sender = (configuredUsername != null && !configuredUsername.isBlank())
                    ? configuredUsername
                    : DEFAULT_FROM;
            helper.setFrom(sender);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException | RuntimeException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new RuntimeException("Email delivery failed: " + ex.getMessage(), ex);
        }
    }
}

