package com.quicktap.pos.net;

import android.content.Context;

import com.quicktap.pos.R;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Secure TLS compatibility for Android versions that predate Network Security Config. */
final class TlsCompat {

    private static volatile SSLSocketFactory socketFactory;

    private TlsCompat() { }

    static SSLSocketFactory socketFactory(Context context) throws Exception {
        SSLSocketFactory existing = socketFactory;
        if (existing != null) return existing;

        synchronized (TlsCompat.class) {
            if (socketFactory != null) return socketFactory;

            X509TrustManager system = trustManager(null);

            Certificate root;
            try (InputStream input = context.getResources()
                    .openRawResource(R.raw.digicert_global_root_g2)) {
                root = CertificateFactory.getInstance("X.509").generateCertificate(input);
            }

            KeyStore bundledStore = KeyStore.getInstance(KeyStore.getDefaultType());
            bundledStore.load(null);
            bundledStore.setCertificateEntry("digicert-global-root-g2", root);
            X509TrustManager bundled = trustManager(bundledStore);

            X509TrustManager combined = new CombinedTrustManager(system, bundled);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{combined}, null);
            socketFactory = ssl.getSocketFactory();
            return socketFactory;
        }
    }

    private static X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new IllegalStateException("No X509 trust manager available");
    }

    private static final class CombinedTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager bundled;

        CombinedTrustManager(X509TrustManager system, X509TrustManager bundled) {
            this.system = system;
            this.bundled = bundled;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException systemFailure) {
                bundled.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            Collections.addAll(issuers, system.getAcceptedIssuers());
            Collections.addAll(issuers, bundled.getAcceptedIssuers());
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}