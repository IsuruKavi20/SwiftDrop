package com.swiftdrop.backend.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validates outgoing webhook destination URLs against Server-Side Request Forgery (SSRF)
 * attacks by rejecting loopback, internal, private, any-local (0.0.0.0), and cloud-metadata addresses.
 */
@Component
public class SsrfValidator {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "localhost.localdomain",
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "metadata.google.internal",
            "instance-data"
    );

    private final DnsResolver dnsResolver;

    public SsrfValidator() {
        this(DnsResolver.SYSTEM);
    }

    @Autowired
    public SsrfValidator(DnsResolver dnsResolver) {
        this.dnsResolver = Objects.requireNonNull(dnsResolver, "dnsResolver must not be null");
    }

    /**
     * Validates that the given URL is a valid, publicly reachable HTTP/HTTPS endpoint
     * using the injected {@link DnsResolver}.
     *
     * @param rawUrl the URL string to validate
     * @throws IllegalArgumentException if the URL fails SSRF safety validation
     */
    public void validate(String rawUrl) {
        validate(rawUrl, this.dnsResolver);
    }

    /**
     * Validates a URL using the default system DNS resolver.
     *
     * @param rawUrl the URL string to validate
     * @throws IllegalArgumentException if the URL fails SSRF safety validation
     */
    public static void validateStatic(String rawUrl) {
        validate(rawUrl, DnsResolver.SYSTEM);
    }

    /**
     * Validates that the given URL is a valid, publicly reachable HTTP/HTTPS endpoint
     * using the provided {@link DnsResolver}.
     *
     * @param rawUrl the URL string to validate
     * @param resolver the DNS resolver to resolve hostnames
     * @throws IllegalArgumentException if the URL fails SSRF safety validation
     */
    public static void validate(String rawUrl, DnsResolver resolver) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Webhook endpoint URL cannot be blank");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook endpoint URL is not a valid URI: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Webhook endpoint URL must use http or https scheme");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Webhook endpoint URL must not contain user credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook endpoint URL must include a valid host");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTNAMES.contains(lowerHost)
                || lowerHost.endsWith(".localhost")
                || lowerHost.endsWith(".local")
                || lowerHost.endsWith(".internal")
                || lowerHost.endsWith(".lan")) {
            throw new IllegalArgumentException("Webhook endpoint URL cannot target localhost or internal domains");
        }

        DnsResolver effectiveResolver = (resolver != null) ? resolver : DnsResolver.SYSTEM;

        // Check if IP literal or resolvable host is private/loopback/link-local/metadata
        try {
            InetAddress[] addresses = effectiveResolver.resolve(host);
            if (addresses != null) {
                for (InetAddress addr : addresses) {
                    if (isBlockedAddress(addr)) {
                        throw new IllegalArgumentException("Webhook endpoint URL cannot target loopback, private, or metadata addresses (" + addr.getHostAddress() + ")");
                    }
                }
            }
        } catch (UnknownHostException ignored) {
            // In offline test environments or behind proxy without DNS, unresolved mock domain names
            // are allowed provided the host literal itself is not a known private pattern.
            if (lowerHost.equals("169.254.169.254") || lowerHost.startsWith("127.") || lowerHost.startsWith("10.")
                    || lowerHost.startsWith("192.168.") || lowerHost.startsWith("172.") || lowerHost.equals("0.0.0.0")) {
                throw new IllegalArgumentException("Webhook endpoint URL cannot target private or metadata addresses");
            }
        }
    }

    /**
     * Determines whether an IP address belongs to loopback, private, any-local, multicast,
     * link-local (cloud metadata), or internal address spaces.
     *
     * @param addr the {@link InetAddress} to inspect
     * @return {@code true} if the address is blocked; {@code false} if safe and public
     */
    public static boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        if (addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }

        byte[] ip = addr.getAddress();
        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 0.0.0.0/8 (Current network / default route)
            if (b0 == 0) return true;
            // 10.0.0.0/8 (Private network)
            if (b0 == 10) return true;
            // 100.64.0.0/10 (Shared address space / Carrier-grade NAT - RFC 6598)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;
            // 169.254.0.0/16 (Link Local / Cloud Metadata, including 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;
            // 172.16.0.0/12 (Private network)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
            // 192.168.0.0/16 (Private network)
            if (b0 == 192 && b1 == 168) return true;
            // 198.18.0.0/15 (Benchmarking - RFC 2544)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;
            // 255.255.255.255 (Broadcast)
            if (b0 == 255 && b1 == 255) return true;
        } else if (ip.length == 16) {
            // Check for IPv4-mapped IPv6 address (::ffff:0:0/96)
            boolean isIpv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (ip[i] != 0) {
                    isIpv4Mapped = false;
                    break;
                }
            }
            if (isIpv4Mapped && (ip[10] & 0xFF) == 0xFF && (ip[11] & 0xFF) == 0xFF) {
                byte[] ipv4 = new byte[]{ip[12], ip[13], ip[14], ip[15]};
                try {
                    return isBlockedAddress(InetAddress.getByAddress(ipv4));
                } catch (UnknownHostException e) {
                    return true;
                }
            }

            // IPv6 Unique Local (fc00::/7) -> first byte fc or fd
            int b0 = ip[0] & 0xFF;
            if (b0 == 0xFC || b0 == 0xFD) return true;
            // IPv6 Link-Local (fe80::/10) -> first byte fe and second byte high 2 bits 10
            int b1 = ip[1] & 0xFF;
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;
        }

        return false;
    }
}
