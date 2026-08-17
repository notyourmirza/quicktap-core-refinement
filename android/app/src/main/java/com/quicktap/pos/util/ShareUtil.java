package com.quicktap.pos.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Shares generated files (PDF reports, receipts). WhatsApp is tried first
 * because that is how shopkeepers actually forward a day closing.
 */
public final class ShareUtil {

    private ShareUtil() { }

    public static Uri uriFor(Context context, File file) {
        return FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
    }

    /** Opens WhatsApp with the file attached; falls back to the system chooser. */
    public static void sharePdf(Context context, File file, String caption) {
        Uri uri = uriFor(context, file);
        Intent base = new Intent(Intent.ACTION_SEND);
        base.setType("application/pdf");
        base.putExtra(Intent.EXTRA_STREAM, uri);
        base.putExtra(Intent.EXTRA_TEXT, caption == null ? "" : caption);
        base.putExtra(Intent.EXTRA_SUBJECT, caption == null ? "" : caption);
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent whatsapp = new Intent(base);
        whatsapp.setPackage("com.whatsapp");
        try {
            context.startActivity(whatsapp);
            return;
        } catch (ActivityNotFoundException ignored) {
            // WhatsApp Business or no WhatsApp at all.
        }

        Intent business = new Intent(base);
        business.setPackage("com.whatsapp.w4b");
        try {
            context.startActivity(business);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall through to the chooser.
        }

        try {
            context.startActivity(Intent.createChooser(base, "Share report"));
        } catch (Exception e) {
            Toast.makeText(context, "No app available to share the report",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Opens the PDF in whatever viewer the device has. */
    public static void openPdf(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriFor(context, file), "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_LONG).show();
        }
    }
}
