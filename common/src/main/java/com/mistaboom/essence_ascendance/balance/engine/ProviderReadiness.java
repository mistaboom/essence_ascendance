package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Objects;

/** Readiness and support are declarations, never inferred from a mod ID or a zero fact count. */
public record ProviderReadiness(Status status, String version, String detail, boolean requiredEvidenceComplete) {
    public enum Status { ABSENT, AVAILABLE, NOT_READY, PARTIALLY_SUPPORTED, UNSUPPORTED, FAILED, DISABLED }
    public ProviderReadiness {
        Objects.requireNonNull(status);
        version = version == null ? "unknown" : version;
        detail = detail == null ? "" : detail;
        if (status == Status.PARTIALLY_SUPPORTED && detail.isBlank())
            throw new IllegalArgumentException("Partial support must describe supported and unsupported data");
    }
    public ProviderReadiness(Status status, String version, String detail) {
        this(status, version, detail, status == Status.AVAILABLE);
    }
    public static ProviderReadiness available() { return new ProviderReadiness(Status.AVAILABLE, "unknown", "Loaded data supported"); }
    public boolean collectable() { return status == Status.AVAILABLE || status == Status.PARTIALLY_SUPPORTED; }
    public void requireSafe(String id, boolean required) {
        if (required && (!collectable() || !requiredEvidenceComplete) && status != Status.ABSENT && status != Status.DISABLED)
            throw new IllegalStateException("Balance generation rejected by provider " + id + " [" + status + "]: " + detail);
    }
}
