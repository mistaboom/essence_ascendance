package com.mistaboom.essence_ascendance.balance.config;

/** A user-editable input failed validation; never silently substitute defaults. */
public final class BalanceConfigException extends IllegalArgumentException {
    public BalanceConfigException(String source, int line, String message) {
        super(source + (line > 0 ? ":" + line : "") + ": " + message);
    }
}
