package com.cupk.aiacademy.installation;

/** Only a stable, non-sensitive error code crosses the installation API boundary. */
public final class InstallationException extends RuntimeException {
    private final int status;
    private final String code;

    public InstallationException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
