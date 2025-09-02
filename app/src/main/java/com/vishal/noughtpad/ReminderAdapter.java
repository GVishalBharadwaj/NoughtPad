package com.vishal.noughtpad;

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

    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ReminderItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public ReminderAdapter() {
        super(DIFF_CALLBACK);
    }

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
        holder.titleTextView.setText(currentItem.title);
        holder.descriptionTextView.setText(currentItem.description);

        if (currentItem.isActive && currentItem.reminderTime > 0) {
            holder.iconImageView.setImageResource(R.drawable.ic_reminder);
            holder.dueDateTextView.setVisibility(View.VISIBLE);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            String formattedDate = sdf.format(new Date(currentItem.reminderTime));
            holder.dueDateTextView.setText("Due: " + formattedDate);
        } else {
            holder.iconImageView.setImageResource(R.drawable.ic_note);
            holder.dueDateTextView.setVisibility(View.GONE);
        }
    }

    class ReminderViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconImageView;
        private final TextView titleTextView;
        private final TextView descriptionTextView;
        private final TextView dueDateTextView;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.item_icon);
            // ✅ This now correctly references the new IDs
            titleTextView = itemView.findViewById(R.id.item_title);
            descriptionTextView = itemView.findViewById(R.id.item_description);
            dueDateTextView = itemView.findViewById(R.id.item_due_date);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<ReminderItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ReminderItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
            return oldItem.title.equals(newItem.title) &&
                    oldItem.description.equals(newItem.description) &&
                    oldItem.reminderTime == newItem.reminderTime &&
                    oldItem.isActive == newItem.isActive;
        }
    };
}