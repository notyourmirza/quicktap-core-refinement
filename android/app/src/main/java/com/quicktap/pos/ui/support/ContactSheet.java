package com.quicktap.pos.ui.support;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.quicktap.pos.R;
import com.quicktap.pos.theme.RemoteTheme;
import com.quicktap.pos.util.SupportContact;

import android.widget.TextView;

/** Premium "Contact admin" bottom sheet. Every route ends in WhatsApp. */
public final class ContactSheet {

    private ContactSheet() { }

    private static final String[] REASONS = {
            "Plan upgrade", "Licence / approval", "Payment", "Technical help", "Other"
    };

    public static void show(Context context, String topic) {
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_contact, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(view);

        int accent = RemoteTheme.primary(context);
        TextView number = view.findViewById(R.id.textSupportNumber);
        number.setText(SupportContact.display(context));

        ChipGroup chips = view.findViewById(R.id.chipReasons);
        for (String reason : REASONS) {
            Chip chip = new Chip(context);
            chip.setText(reason);
            chip.setCheckable(true);
            chip.setChipStrokeWidth(context.getResources().getDisplayMetrics().density);
            chips.addView(chip);
        }
        int preselect = 0;
        for (int i = 0; i < REASONS.length; i++) {
            if (topic != null && REASONS[i].equalsIgnoreCase(topic)) preselect = i;
        }
        ((Chip) chips.getChildAt(preselect)).setChecked(true);

        TextInputEditText message = view.findViewById(R.id.inputMessage);

        view.findViewById(R.id.buttonWhatsapp).setOnClickListener(v -> {
            String reason = REASONS[0];
            for (int i = 0; i < chips.getChildCount(); i++) {
                Chip c = (Chip) chips.getChildAt(i);
                if (c.isChecked()) reason = c.getText().toString();
            }
            String body = "*" + com.quicktap.pos.util.AppPrefs.get(context).getThemeAppName()
                    + " · " + reason + "*\n"
                    + (message.getText() == null ? "" : message.getText().toString().trim())
                    + SupportContact.signature(context);
            SupportContact.chat(context, body);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonCopyNumber).setOnClickListener(v -> {
            SupportContact.copy(context, SupportContact.display(context));
            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.viewAccentDot).setBackgroundColor(accent);
        dialog.show();
    }
}
