package com.swiftdrop.backend.notification.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Production implementation of {@link DnsResolver} using Java's standard network DNS lookup.
 */
@Component
public class SystemDnsResolver implements DnsResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
