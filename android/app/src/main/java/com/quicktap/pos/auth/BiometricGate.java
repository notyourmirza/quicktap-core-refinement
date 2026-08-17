package com.quicktap.pos.auth;

import android.content.Context;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Thin wrapper around AndroidX BiometricPrompt for fingerprint / face unlock.
 * Falls back gracefully on devices with no enrolled biometrics.
 */
public final class BiometricGate {

    public interface Listener {
        void onSuccess();
        /** fatal = the user cannot retry (no hardware, too many attempts, cancelled). */
        void onFailure(String message, boolean fatal);
    }

    private static final int AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private BiometricGate() { }

    /** True when the device has usable biometrics or a device PIN/pattern. */
    public static boolean isAvailable(Context context) {
        int status = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS);
        return status == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static String unavailableReason(Context context) {
        switch (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "This device has no fingerprint sensor";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "Fingerprint sensor is unavailable right now";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "Add a fingerprint or screen lock in Android settings first";
            default:
                return "Fingerprint unlock is not available";
        }
    }

    public static void prompt(FragmentActivity activity, String title, String subtitle, Listener listener) {
        if (!isAvailable(activity)) {
            listener.onFailure(unavailableReason(activity), true);
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        listener.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        listener.onFailure(String.valueOf(errString), true);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        listener.onFailure("Fingerprint not recognised", false);
                    }
                });

        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build());
    }
}
