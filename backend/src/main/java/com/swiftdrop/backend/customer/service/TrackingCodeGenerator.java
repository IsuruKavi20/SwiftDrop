package com.swiftdrop.backend.customer.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates tracking numbers and public tracking tokens. Both are random, not sequential, so nobody can
 * walk through other people's deliveries by trying SD-000001, SD-000002, ...
 */
@Component
public class TrackingCodeGenerator {

    /** No 0/O or 1/I, so a tracking number read out over the phone is not misheard. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TRACKING_NUMBER_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    /** e.g. SD-7K3M9QW2XA (13 characters). */
    public String newTrackingNumber() {
        StringBuilder number = new StringBuilder("SD-");
        for (int i = 0; i < TRACKING_NUMBER_LENGTH; i++) {
            number.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return number.toString();
    }

    /** 256 random bits, URL-safe (43 characters): for public tracking links. */
    public String newPublicTrackingToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
