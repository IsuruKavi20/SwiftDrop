package com.swiftdrop.backend.dispatcher.dto;

import java.util.UUID;

/** An active person with the Courier role who has no courier profile yet. */
public record CourierCandidateResponse(UUID userId, String name) { }
