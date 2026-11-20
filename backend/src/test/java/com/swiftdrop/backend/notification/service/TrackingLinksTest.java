package com.swiftdrop.backend.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingLinksTest {

    private final TrackingLinks links = new TrackingLinks("https://app.getswiftdrop.me/");

    @Test
    void usesTheUnguessableLinkCodeWhenThereIsOne() {
        assertThat(links.forDelivery("q3Z-x9", "SD-7K3M9QW2XA"))
                .isEqualTo("https://app.getswiftdrop.me/track/link/q3Z-x9");
    }

    @Test
    void fallsBackToTheTrackingNumber() {
        assertThat(links.forDelivery(null, "SD-7K3M9QW2XA")).isEqualTo("https://app.getswiftdrop.me/track/SD-7K3M9QW2XA");
    }

    @Test
    void refusesAPlainHttpWebsiteOutsideLocalhost() {
        assertThatThrownBy(() -> new TrackingLinks("http://app.getswiftdrop.me")).isInstanceOf(IllegalArgumentException.class);
    }
}
