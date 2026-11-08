package com.swiftdrop.backend.identity.entity;

/** The auth service's account status, plus PENDING_SYNC for a row we haven't filled in from auth yet. */
public enum AppUserStatus {
    PENDING_SYNC,
    ACTIVE,
    LOCKED,
    DISABLED,
    DELETED
}
