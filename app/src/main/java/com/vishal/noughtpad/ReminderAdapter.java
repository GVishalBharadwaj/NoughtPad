package com.vishal.noughtpad;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderAdapter extends ListAdapter<ReminderItem, RecyclerView.ViewHolder> {

    private static final int TYPE_REMINDER = 0;
    private static final int TYPE_TRANSACTION = 1;

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

    @Override
    public int getItemViewType(int position) {
        ReminderItem item = getItem(position);
        if ("RECEIPT".equals(item.category)) {
            return TYPE_TRANSACTION;
        }
        return TYPE_REMINDER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_TRANSACTION) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new TransactionViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_reminder, parent, false);
            return new ReminderViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ReminderItem item = getItem(position);
        if (holder instanceof TransactionViewHolder) {
            ((TransactionViewHolder) holder).bind(item);
        } else if (holder instanceof ReminderViewHolder) {
            ((ReminderViewHolder) holder).bind(item);
        }
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView title, date, amount, category;
        private final ImageView icon;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.trans_title);
            date = itemView.findViewById(R.id.trans_date);
            amount = itemView.findViewById(R.id.trans_amount);
            category = itemView.findViewById(R.id.trans_category);
            icon = itemView.findViewById(R.id.trans_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
        }

        void bind(ReminderItem item) {
            title.setText(item.title.isEmpty() ? "Unknown Vendor" : item.title);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            date.setText(item.reminderTime > 0 ? sdf.format(new Date(item.reminderTime)) : "No Date");

            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            String amountText = format.format(item.amount);

            boolean isIncome = "income".equalsIgnoreCase(item.type);

            if (isIncome) {
                amount.setText("+ " + amountText);
                amount.setTextColor(Color.parseColor("#69F0AE")); // Green accent
            } else {
                amount.setText("- " + amountText);
                amount.setTextColor(Color.parseColor("#FF5252")); // Red accent
            }

            // Category from tags
            String cat = "General";
            if (item.tags != null && !item.tags.isEmpty()) {
                cat = item.tags.split(",")[0];
            }
            category.setText(cat);
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

        void bind(ReminderItem currentItem) {
            titleTextView.setText(currentItem.title);
            descriptionTextView.setText(currentItem.description);

            if (currentItem.isActive && currentItem.reminderTime > 0) {
                iconImageView.setImageResource(R.drawable.ic_reminder);
                dueDateTextView.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                String formattedDate = sdf.format(new Date(currentItem.reminderTime));
                dueDateTextView.setText("Due: " + formattedDate);
            } else {
                iconImageView.setImageResource(R.drawable.ic_note);
                dueDateTextView.setVisibility(View.GONE);
            }
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
                    oldItem.isActive == newItem.isActive &&
                    oldItem.amount == newItem.amount &&
                    oldItem.category.equals(newItem.category) &&
                    (oldItem.tags == null ? newItem.tags == null : oldItem.tags.equals(newItem.tags));
        }
    };
}