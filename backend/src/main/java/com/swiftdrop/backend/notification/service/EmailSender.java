package com.swiftdrop.backend.notification.service;

public interface EmailSender {
    void send(String to, String subject, String bodyHtml);
}

