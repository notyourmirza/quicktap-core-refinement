package com.quicktap.pos.net;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parsed result of one API call against the QuickTap REST API.
 *
 * The server always answers with the same envelope:
 * {"success":bool,"message":string,"data":mixed,"code":string,"timestamp":long}
 */
public final class ApiResponse {

    /** False only when the request never reached the server (no internet, DNS, timeout). */
    public boolean networkOk;
    /** True when the server answered with success:true. */
    public boolean success;
    public int httpCode;
    public String message = "";
    /** Machine readable error code, e.g. TOKEN_EXPIRED, DEVICE_MISMATCH, SHOP_INACTIVE. */
    public String errorCode = "";
    /** data when it is a JSON object, otherwise null. */
    public JSONObject data;
    /** data when it is a JSON array, otherwise null. */
    public JSONArray list;

    public static ApiResponse offline(String message) {
        ApiResponse r = new ApiResponse();
        r.networkOk = false;
        r.success = false;
        r.message = message == null || message.isEmpty() ? "No internet connection" : message;
        r.errorCode = "OFFLINE";
        return r;
    }

    public boolean is(String code) { return errorCode != null && errorCode.equals(code); }

    public JSONObject dataOrEmpty() { return data == null ? new JSONObject() : data; }

    @Override public String toString() {
        return "ApiResponse{" + httpCode + " success=" + success + " code=" + errorCode + " msg=" + message + '}';
    }
}
