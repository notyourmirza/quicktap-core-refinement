package com.quicktap.pos.license;

/** Normalised response from the licence server. */
public class LicenseResult {
    public boolean networkOk;
    public boolean success;
    /** PENDING | APPROVED | REJECTED | BLOCKED | EXPIRED */
    public String status = "PENDING";
    public String message = "";
    public String token;
    public long expiresAt;
}
