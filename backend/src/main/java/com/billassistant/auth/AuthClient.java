package com.billassistant.auth;

public enum AuthClient {
    WEB("web"), APP("app");

    private final String value;

    AuthClient(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Tokens and Redis sessions issued before client separation belong to the Web client. */
    public static AuthClient fromStoredValue(String value) {
        if (value == null || "web".equals(value)) return WEB;
        if ("app".equals(value)) return APP;
        throw new IllegalArgumentException("Unknown authentication client");
    }
}
