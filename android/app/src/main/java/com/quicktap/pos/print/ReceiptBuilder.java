package com.quicktap.pos.print;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.util.AppPrefs;
import com.quicktap.pos.util.DateUtil;
import com.quicktap.pos.util.Money;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Turns a saved bill into an ESC/POS byte stream. Layout adapts to the
 * configured paper width (32 chars for 58mm, 48 chars for 80mm).
 */
public class ReceiptBuilder {

    private final AppPrefs prefs;
    private final int width;
    private final ReceiptTemplates.Template template;

    public ReceiptBuilder(AppPrefs prefs) {
        this.prefs = prefs;
        this.width = prefs.getPaperChars();
        this.template = ReceiptTemplates.byKey(prefs.getReceiptTemplate());
    }

    public byte[] build(Bill bill, List<BillItem> items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(EscPos.INIT);
            out.write(EscPos.ALIGN_CENTER);

            // ----- optional shop logo -----
            byte[] logo = logoBytes();
            if (logo.length > 0) {
                out.write(logo);
                line(out, "");
            }

            // ----- store header -----
            out.write(EscPos.DOUBLE_ON);
            line(out, clip(prefs.getStoreName().toUpperCase(Locale.getDefault()), width / 2));
            out.write(EscPos.DOUBLE_OFF);
            if (notEmpty(prefs.getStoreAddress())) wrap(out, prefs.getStoreAddress());
            if (notEmpty(prefs.getStorePhone())) line(out, "Tel: " + prefs.getStorePhone());
            if (prefs.isReceiptNoteEnabled() && notEmpty(prefs.getReceiptNote())) {
                line(out, "");
                wrap(out, prefs.getReceiptNote());
            }

            line(out, "");
            out.write(EscPos.BOLD_ON);
            line(out, bill.paid ? "SALES INVOICE" : "PROFORMA INVOICE");
            out.write(EscPos.BOLD_OFF);
            rule(out, true);

            // ----- bill meta -----
            out.write(template.centeredMeta ? EscPos.ALIGN_CENTER : EscPos.ALIGN_LEFT);
            line(out, pair("Invoice", bill.invoiceNo));
            line(out, pair("Date", DateUtil.receipt(bill.createdAt)));
            line(out, pair("Order", readableType(bill.orderType)));
            if (notEmpty(bill.tableNo)) line(out, pair("Table", bill.tableNo));
            if (notEmpty(bill.customerName)) line(out, pair("Customer", bill.customerName));
            if (notEmpty(bill.customerPhone)) line(out, pair("Phone", bill.customerPhone));
            if (notEmpty(bill.address)) wrap(out, "Address: " + bill.address);
            out.write(EscPos.ALIGN_LEFT);
            rule(out, false);

            // ----- items -----
            out.write(EscPos.BOLD_ON);
            line(out, columns("ITEM", "QTY", "AMOUNT"));
            out.write(EscPos.BOLD_OFF);
            rule(out, false);
            for (BillItem item : items) {
                if (template.showItemPrice) {
                    line(out, clip(item.name, width));
                    line(out, columns("  @ " + Money.format(item.price),
                            String.valueOf(item.qty), Money.format(item.lineTotal())));
                } else {
                    line(out, columns(item.name, String.valueOf(item.qty),
                            Money.format(item.lineTotal())));
                }
            }
            rule(out, false);

            // ----- totals -----
            line(out, right("Subtotal", Money.format(bill.subtotal)));
            if (bill.discount > 0) line(out, right("Discount", "-" + Money.format(bill.discount)));
            if (bill.tax > 0) line(out, right("Tax", Money.format(bill.tax)));
            rule(out, true);
            out.write(EscPos.BOLD_ON);
            out.write(EscPos.DOUBLE_HEIGHT_ON);
            line(out, right("TOTAL " + prefs.getCurrency(), Money.format(bill.total)));
            out.write(EscPos.DOUBLE_OFF);
            out.write(EscPos.BOLD_OFF);
            rule(out, true);
            line(out, right("Payment", bill.paid ? "PAID" : "UNPAID"));
            if (notEmpty(bill.notes)) {
                rule(out, false);
                wrap(out, "Note: " + bill.notes);
            }

            // ----- footer -----
            rule(out, true);
            out.write(EscPos.ALIGN_CENTER);
            if (template.thankYouBlock) line(out, "Thank you for your business");
            if (notEmpty(prefs.getReceiptFooter())) wrap(out, prefs.getReceiptFooter());
            line(out, "");
            line(out, "Powered by " + prefs.getThemeAppName());
            out.write(EscPos.FEED_CUT);
        } catch (IOException e) {
            // ByteArrayOutputStream does not throw in practice.
            throw new IllegalStateException(e);
        }
        return out.toByteArray();

    }

    /** Simple centred test slip used by "Test print" in printer settings. */
    public byte[] testPage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(EscPos.INIT);
            out.write(EscPos.ALIGN_CENTER);
            out.write(EscPos.DOUBLE_ON);
            line(out, prefs.getThemeAppName());
            out.write(EscPos.DOUBLE_OFF);
            line(out, "Printer test successful");
            line(out, prefs.getStoreName());
            line(out, DateUtil.receipt(System.currentTimeMillis()));
            line(out, divider('='));
            line(out, "Paper width: " + width + " chars");
            out.write(EscPos.FEED_CUT);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    // ---------------- text layout helpers ----------------

    private void line(ByteArrayOutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Draws the template's divider; heavy rules use the template character. */
    private void rule(ByteArrayOutputStream out, boolean heavy) throws IOException {
        char c = template.divider;
        if (c == ' ') { line(out, ""); return; }
        line(out, divider(heavy ? c : '-'));
    }

    private String divider(char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) sb.append(c);
        return sb.toString();
    }

    private String pair(String label, String value) {
        return padRight(label + ":", 10) + safe(value);
    }

    /** left | qty | right, with the right column flush to the paper edge. */
    private String columns(String left, String qty, String right) {
        int rightWidth = 10;
        int qtyWidth = 5;
        int leftWidth = Math.max(1, width - rightWidth - qtyWidth);
        return padRight(clip(left, leftWidth), leftWidth)
                + padLeft(qty, qtyWidth)
                + padLeft(right, rightWidth);
    }

    private String right(String label, String value) {
        int valueWidth = 12;
        int labelWidth = Math.max(1, width - valueWidth);
        return padRight(clip(label, labelWidth), labelWidth) + padLeft(value, valueWidth);
    }

    private String clip(String text, int max) {
        String s = safe(text);
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String padRight(String text, int size) {
        StringBuilder sb = new StringBuilder(safe(text));
        while (sb.length() < size) sb.append(' ');
        return sb.toString();
    }

    private String padLeft(String text, int size) {
        StringBuilder sb = new StringBuilder(safe(text));
        while (sb.length() < size) sb.insert(0, ' ');
        return sb.toString();
    }

    private String safe(String value) { return value == null ? "" : value; }

    /** Word-wraps long lines so nothing is cut off on narrow paper. */
    private void wrap(ByteArrayOutputStream out, String text) throws IOException {
        String value = safe(text).trim();
        while (value.length() > width) {
            int cut = value.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            line(out, value.substring(0, cut).trim());
            value = value.substring(cut).trim();
        }
        if (!value.isEmpty()) line(out, value);
    }

    /** Optional monochrome logo printed above the store name. */
    private byte[] logoBytes() {
        if (!prefs.isReceiptLogoEnabled()) return new byte[0];
        String path = prefs.getReceiptLogoPath();
        if (path == null || path.isEmpty() || !new File(path).exists()) return new byte[0];
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) return new byte[0];
            int dots = width >= 42 ? 384 : 240;
            byte[] raster = EscPos.raster(bitmap, dots);
            bitmap.recycle();
            return raster;
        } catch (Throwable t) {
            return new byte[0];
        }
    }


    private boolean notEmpty(String value) { return value != null && !value.trim().isEmpty(); }

    private String readableType(String type) {
        switch (type) {
            case com.quicktap.pos.data.entity.Bill.TYPE_TAKE_AWAY: return "Take Away";
            case com.quicktap.pos.data.entity.Bill.TYPE_DELIVERY: return "Delivery";
            default: return "Dine In";
        }
    }
}
