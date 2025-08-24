package com.example.smartc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderAdapter extends ListAdapter<ReminderItem, ReminderAdapter.ReminderViewHolder> {

    // ✅ START: New code for handling clicks
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ReminderItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    // ✅ END: New code for handling clicks

    public ReminderAdapter() {
        super(DIFF_CALLBACK);
    }

    // ... (DIFF_CALLBACK is unchanged)
    private static final DiffUtil.ItemCallback<ReminderItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ReminderItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
            return oldItem.content.equals(newItem.content) &&
                    oldItem.reminderTime == newItem.reminderTime &&
                    oldItem.isActive == newItem.isActive;
        }
    };

    @NonNull
    @Override
    public ReminderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_reminder, parent, false);
        return new ReminderViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderViewHolder holder, int position) {
        ReminderItem currentItem = getItem(position);

        // ✅ Use the new 'title' field instead of 'content'
        holder.contentTextView.setText(currentItem.title);

        if ("REMINDER".equals(currentItem.category) || "BILL".equals(currentItem.category) || "TICKET".equals(currentItem.category) || "TASK".equals(currentItem.category)) {
            holder.iconImageView.setImageResource(R.drawable.ic_reminder);

            // If there's a valid reminder time, show and format it
            if (currentItem.reminderTime > 0) {
                holder.dueDateTextView.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                String formattedDate = sdf.format(new Date(currentItem.reminderTime));
                holder.dueDateTextView.setText("Due: " + formattedDate);
            } else {
                holder.dueDateTextView.setVisibility(View.GONE);
            }
        } else {
            // This is for NOTE or RECEIPT categories
            holder.iconImageView.setImageResource(R.drawable.ic_note);
            // We can show a preview of the description for notes
            holder.dueDateTextView.setVisibility(View.VISIBLE);
            holder.dueDateTextView.setText(currentItem.description);
        }
    }
    class ReminderViewHolder extends RecyclerView.ViewHolder {
        // ... (variable declarations are unchanged)
        private final ImageView iconImageView;
        private final TextView contentTextView;
        private final TextView dueDateTextView;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            contentTextView = itemView.findViewById(R.id.item_content);
            dueDateTextView = itemView.findViewById(R.id.item_due_date);

            // ✅ START: New code to make the item clickable
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
            // ✅ END: New code to make the item clickable
        }
    }
}