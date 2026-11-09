package com.swiftdrop.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[MOCK SMS GATEWAY] Dispatching SMS to {}: \"{}\"", phoneNumber, message);
    }
}

