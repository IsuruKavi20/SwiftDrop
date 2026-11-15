package com.swiftdrop.backend.pricing;

/** Where quotes get their rates: the newest published version, or the server's settings while none is published. */
public interface TariffSource {

    Tariff current();
}
