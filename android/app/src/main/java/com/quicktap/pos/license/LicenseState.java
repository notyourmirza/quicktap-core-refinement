package com.quicktap.pos.license;

import com.quicktap.pos.net.ApiResponse;

import org.json.JSONObject;

/**
 * Immutable snapshot of what the SERVER said about this account's licence.
 *
 * Nothing in this class decides anything by itself: {@link #unlocked} is only
 * true when the server explicitly answered {@code unlocked:true} together with
 * an {@code ACTIVE} status. Any parsing problem, any unknown payload and any
 * network failure fail closed.
 */
public final class LicenseState {

    // ---- states the UI can route on ----
    public static final String NO_ACCOUNT = "NO_ACCOUNT";
    public static final String PENDING    = "PENDING";
    public static final String ACTIVE     = "ACTIVE";
    public static final String EXPIRED    = "EXPIRED";
    public static final String REVOKED    = "REVOKED";
    public static final String SUSPENDED  = "SUSPENDED";
    public static final String BLOCKED    = "BLOCKED";
    public static final String INVALID    = "INVALID";
    /** The server could not be reached — never treat this as a licence. */
    public static final String OFFLINE    = "OFFLINE";
    public static final String UNKNOWN    = "UNKNOWN";

    public String state = UNKNOWN;
    public boolean unlocked;
    public boolean confirmed;
    public boolean networkOk;
    public boolean success;
    public String message = "";
    public String code = "";
    public String requestStatus = "";
    public String durationLabel = "";
    public long expiresAtMs;
    public long serverTimeMs;
    /** -1 when the server did not send one (lifetime licence). */
    public int daysLeft = -1;

    // ------------------------------------------------------------------ //

    /** Maps one API answer onto a licence state. Unknown shapes fail closed. */
    public static LicenseState from(ApiResponse res) {
        LicenseState s = new LicenseState();
        if (res == null) {
            s.message = "Unexpected error.";
            return s;
        }
        s.networkOk = res.networkOk;
        s.success = res.success;
        s.message = res.message == null ? "" : res.message;
        s.code = res.errorCode == null ? "" : res.errorCode;

        if (!res.networkOk) {
            s.state = OFFLINE;
            s.message = res.message == null || res.message.isEmpty()
                    ? "Cannot reach the licence server." : res.message;
            return s;
        }

        if (res.success) {
            JSONObject d = res.dataOrEmpty();
            String status = d.optString("license_status", "");
            s.state = normalise(status);
            // Account-level overrides always win over the licence row.
            String account = d.optString("account_status", "");
            if ("BLOCKED".equalsIgnoreCase(account)) s.state = BLOCKED;
            else if ("suspended".equalsIgnoreCase(account)) s.state = SUSPENDED;

            s.unlocked = ACTIVE.equals(s.state) && d.optBoolean("unlocked", false);
            s.confirmed = d.optBoolean("confirmed", false);
            s.requestStatus = d.optString("request_status", "");
            s.durationLabel = d.optString("duration_label", "");
            s.expiresAtMs = d.optLong("expires_at_ms", 0L);
            s.serverTimeMs = d.optLong("server_time_ms", 0L);
            s.daysLeft = d.isNull("days_left") ? -1 : d.optInt("days_left", -1);
            if (s.state.equals(UNKNOWN)) s.unlocked = false;
            return s;
        }

        // Error answers carry the reason in the machine-readable code.
        switch (s.code) {
            case "LICENSE_EXPIRED":        s.state = EXPIRED; break;
            case "LICENSE_REVOKED":        s.state = REVOKED; break;
            case "LICENSE_PENDING":
            case "LICENSE_INACTIVE":       s.state = PENDING; break;
            case "ACCOUNT_BLOCKED":
            case "DEVICE_BLOCKED":
            case "USER_DISABLED":          s.state = BLOCKED; break;
            case "SHOP_INACTIVE":          s.state = SUSPENDED; break;
            case "NO_TOKEN":
            case "TOKEN_EXPIRED":
            case "TOKEN_INVALID":          s.state = NO_ACCOUNT; break;
            case "LICENSE_NOT_FOUND":
            case "LICENSE_FOREIGN":
            case "DEVICE_MISMATCH":
            case "BAD_CREDENTIALS":        s.state = INVALID; break;
            default:                       s.state = UNKNOWN; break;
        }
        return s;
    }

    private static String normalise(String status) {
        if (status == null) return UNKNOWN;
        switch (status.toUpperCase()) {
            case "ACTIVE":    return ACTIVE;
            case "PENDING":   return PENDING;
            case "EXPIRED":   return EXPIRED;
            case "REVOKED":   return REVOKED;
            case "BLOCKED":   return BLOCKED;
            case "SUSPENDED": return SUSPENDED;
            default:          return UNKNOWN;
        }
    }

    /** Short headline for the state, used by the banner and the lock screens. */
    public String headline() {
        switch (state) {
            case ACTIVE:    return "Licence active";
            case PENDING:   return "Licence status: Pending";
            case EXPIRED:   return "Your licence has expired";
            case REVOKED:   return "Your licence has been revoked";
            case SUSPENDED: return "Your account is suspended";
            case BLOCKED:   return "Your account has been blocked";
            case INVALID:   return "Licence could not be verified";
            case OFFLINE:   return "No connection to the licence server";
            case NO_ACCOUNT:return "Sign in required";
            default:        return "Licence could not be verified";
        }
    }

    public String describe() {
        if (message != null && !message.isEmpty()) return message;
        return headline();
    }

    /** Human "License: 47 Days" line, always from server data. */
    public String durationLine() {
        return durationLabel == null || durationLabel.isEmpty()
                ? "License: Active" : "License: " + durationLabel;
    }
}
