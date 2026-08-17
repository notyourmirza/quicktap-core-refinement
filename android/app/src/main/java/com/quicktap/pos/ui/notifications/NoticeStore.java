package com.quicktap.pos.ui.notifications;

import android.content.Context;

import com.quicktap.pos.util.AppPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Announcements published by the Super Admin. The last payload is cached so the
 * notification centre still opens instantly when the counter is offline.
 */
public final class NoticeStore {

    public static final class Notice {
        public final long id;
        public final String title;
        public final String body;
        public final String level;
        public final String date;

        Notice(long id, String title, String body, String level, String date) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.level = level == null || level.isEmpty() ? "info" : level;
            this.date = date;
        }
    }

    private NoticeStore() { }

    public static List<Notice> cached(Context context) {
        return parse(AppPrefs.get(context).getNotices());
    }

    public static void cache(Context context, String json) {
        AppPrefs.get(context).setNotices(json == null ? "" : json);
    }

    public static List<Notice> parse(String json) {
        List<Notice> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                out.add(new Notice(
                        o.optLong("id", i),
                        o.optString("title", "Announcement"),
                        o.optString("body", ""),
                        o.optString("level", "info"),
                        firstNonEmpty(o.optString("created_at", ""), o.optString("starts_at", ""))));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty() && !"null".equals(a)) return a;
        return b == null || "null".equals(b) ? "" : b;
    }
}
