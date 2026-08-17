package com.quicktap.pos.ui.notifications;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quicktap.pos.R;
import com.quicktap.pos.theme.RemoteTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders admin announcements as premium cards. */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.Holder> {

    private final List<NoticeStore.Notice> items = new ArrayList<>();

    public void submit(List<NoticeStore.Notice> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NoticeStore.Notice notice = items.get(position);
        int accent = accentFor(holder.itemView, notice.level);

        holder.title.setText(notice.title);
        holder.body.setText(notice.body);
        holder.body.setVisibility(notice.body == null || notice.body.isEmpty()
                ? View.GONE : View.VISIBLE);
        holder.level.setText(notice.level.toUpperCase(Locale.ROOT));
        holder.level.setTextColor(accent);
        holder.date.setText(notice.date == null || notice.date.isEmpty() ? "" : notice.date);
        holder.date.setVisibility(holder.date.getText().length() == 0 ? View.GONE : View.VISIBLE);
        holder.accent.setBackgroundColor(accent);
    }

    private int accentFor(View view, String level) {
        switch (level == null ? "info" : level.toLowerCase(Locale.ROOT)) {
            case "critical": return Color.parseColor("#DC2626");
            case "warning":  return Color.parseColor("#D97706");
            default:         return RemoteTheme.primary(view.getContext());
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title, body, level, date;
        final View accent;

        Holder(@NonNull View view) {
            super(view);
            title = view.findViewById(R.id.textNoticeTitle);
            body = view.findViewById(R.id.textNoticeBody);
            level = view.findViewById(R.id.textNoticeLevel);
            date = view.findViewById(R.id.textNoticeDate);
            accent = view.findViewById(R.id.viewNoticeAccent);
        }
    }
}
