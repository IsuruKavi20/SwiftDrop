package com.swiftdrop.backend.notification.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Abstraction for DNS address resolution to allow deterministic testing of
 * SSRF validation without relying on external network DNS resolution.
 */
@FunctionalInterface
public interface DnsResolver {

    /**
     * Resolves the given hostname into an array of IP addresses.
     *
     * @param host the hostname to resolve
     * @return array of resolved IP addresses
     * @throws UnknownHostException if host cannot be resolved
     */
    InetAddress[] resolve(String host) throws UnknownHostException;

    /**
     * Default system resolver delegating to {@link InetAddress#getAllByName(String)}.
     */
    DnsResolver SYSTEM = InetAddress::getAllByName;
}
