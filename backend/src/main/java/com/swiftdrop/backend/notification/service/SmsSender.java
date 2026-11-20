package com.swiftdrop.backend.notification.service;

public interface SmsSender {
    void send(String phoneNumber, String message);
}

