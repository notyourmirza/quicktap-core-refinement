package com.quicktap.pos.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Minimal remote image loader for the marketplace cards. No third-party
 * library: one background fetch per URL, cached in memory for the session.
 */
public final class ImageLoader {

    private static final Map<String, Bitmap> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ImageLoader() { }

    public static void load(ImageView view, String url) {
        if (view == null) return;
        if (url == null || url.trim().isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        view.setTag(url);
        AppExecutors.io().execute(() -> {
            Bitmap bitmap = fetch(url);
            if (bitmap == null) return;
            CACHE.put(url, bitmap);
            AppExecutors.main().post(() -> {
                // The row may have been recycled onto a different item.
                if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            try (InputStream in = conn.getInputStream()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
