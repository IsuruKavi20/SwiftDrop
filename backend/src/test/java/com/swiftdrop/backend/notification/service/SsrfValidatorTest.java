package com.swiftdrop.backend.notification.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfValidatorTest {

    private final byte[] publicIp = new byte[]{(byte) 93, (byte) 184, (byte) 216, 34};

    @Test
    void validate_validPublicHttpAndHttpsUrls_passes() {
        DnsResolver resolver = host -> new InetAddress[]{InetAddress.getByAddress(host, publicIp)};
        SsrfValidator validator = new SsrfValidator(resolver);

        assertThatCode(() -> validator.validate("https://api.partner.com/webhook"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("http://example.com:8080/events"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_blankOrNullUrl_throwsIllegalArgumentException() {
        SsrfValidator validator = new SsrfValidator();

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @Test
    void validate_unsupportedScheme_throwsIllegalArgumentException() {
        SsrfValidator validator = new SsrfValidator();

        assertThatThrownBy(() -> validator.validate("ftp://example.com/file"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use http or https scheme");

        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use http or https scheme");
    }

    @Test
    void validate_userCredentialsInUrl_throwsIllegalArgumentException() {
        SsrfValidator validator = new SsrfValidator();

        assertThatThrownBy(() -> validator.validate("https://user:password@example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain user credentials");
    }

    @Test
    void validate_blockedHostnames_throwsIllegalArgumentException() {
        SsrfValidator validator = new SsrfValidator();

        String[] blockedHosts = {
                "http://localhost/hook",
                "http://localhost.localdomain/hook",
                "http://127.0.0.1/hook",
                "http://[::1]/hook",
                "http://0.0.0.0/hook",
                "http://metadata.google.internal/computeMetadata/v1",
                "http://instance-data/latest/meta-data",
                "http://myhost.localhost/hook",
                "http://service.local/hook",
                "http://api.internal/hook",
                "http://app.lan/hook"
        };

        for (String url : blockedHosts) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void validate_domainResolvingToPrivateOrLoopback_throwsIllegalArgumentException() {
        DnsResolver loopbackResolver = host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})
        };
        SsrfValidator loopbackValidator = new SsrfValidator(loopbackResolver);

        assertThatThrownBy(() -> loopbackValidator.validate("https://malicious.example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot target loopback, private, or metadata addresses");

        DnsResolver privateResolver = host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{10, 0, 0, 1})
        };
        SsrfValidator privateValidator = new SsrfValidator(privateResolver);

        assertThatThrownBy(() -> privateValidator.validate("https://rebinding.example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot target loopback, private, or metadata addresses");
    }

    @Test
    void validate_domainResolvingToAnyLocalZeroAddress_throwsIllegalArgumentException() {
        DnsResolver zeroResolver = host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{0, 0, 0, 0})
        };
        SsrfValidator validator = new SsrfValidator(zeroResolver);

        assertThatThrownBy(() -> validator.validate("https://callback.partner.com/events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0.0.0");
    }

    @Test
    void validate_unknownHostFallback_allowsPublicDomain_blocksPrivateLiterals() {
        DnsResolver throwingResolver = host -> {
            throw new UnknownHostException("DNS failure for " + host);
        };
        SsrfValidator validator = new SsrfValidator(throwingResolver);

        // Public unresolved mock domain allowed in offline environments
        assertThatCode(() -> validator.validate("https://offline-mock.partner.com/events"))
                .doesNotThrowAnyException();

        // Private IP literals still blocked even if resolution throws
        assertThatThrownBy(() -> validator.validate("http://169.254.169.254/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("http://10.1.2.3/admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("http://192.168.1.1/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isBlockedAddress_correctlyIdentifiesUnsafeAddresses() throws Exception {
        assertThat(SsrfValidator.isBlockedAddress(null)).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("127.1.2.3"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("0.0.0.0"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("10.254.254.254"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("172.31.255.254"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("192.168.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("255.255.255.255"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("::1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("fe80::1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("::ffff:127.0.0.1"))).isTrue();
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByName("::ffff:10.0.0.1"))).isTrue();

        // Safe public IP
        assertThat(SsrfValidator.isBlockedAddress(InetAddress.getByAddress(publicIp))).isFalse();
    }
}
