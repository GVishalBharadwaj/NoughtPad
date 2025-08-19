package com.example.smartc;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Extends ListAdapter for high performance and automatic animations
public class ReminderAdapter extends ListAdapter<ReminderItem, ReminderAdapter.ReminderViewHolder> {

    private OnItemDeleteListener deleteListener;
    private OnItemClickListener clickListener;

    // Constructor passes the DiffUtil callback to the ListAdapter
    public ReminderAdapter() {
        super(new ReminderDiffCallback());
    }

    @NonNull
    @Override
    public ReminderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_reminder, parent, false);
        return new ReminderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderViewHolder holder, int position) {
        ReminderItem currentItem = getItem(position);
        holder.bind(currentItem);
    }

    // ViewHolder class holds the views for a single row
    class ReminderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewType;
        private final TextView textViewContent;
        private final TextView textViewDetails;
        private final ImageView buttonDelete;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewType = itemView.findViewById(R.id.textview_type);
            textViewContent = itemView.findViewById(R.id.textview_content);
            textViewDetails = itemView.findViewById(R.id.textview_details);
            buttonDelete = itemView.findViewById(R.id.button_delete);

            // Set up the click listener for the delete button
            buttonDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (deleteListener != null && position != RecyclerView.NO_POSITION) {
                    deleteListener.onItemDelete(getItem(position));
                }
            });

            // Set up the click listener for the entire card
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (clickListener != null && position != RecyclerView.NO_POSITION) {
                    clickListener.onItemClick(getItem(position));
                }
            });
        }

        // Binds the data from the ReminderItem to the views
        public void bind(ReminderItem currentItem) {
            textViewContent.setText(currentItem.content);
            textViewType.setText(currentItem.type);

            GradientDrawable typeBackground = (GradientDrawable) textViewType.getBackground();

            if ("REMINDER".equals(currentItem.type)) {
                if (currentItem.isActive) {
                    // Active Reminder
                    textViewDetails.setVisibility(View.VISIBLE);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                    String dateString = dateFormat.format(new Date(currentItem.reminderTime));

                    String detailsText = currentItem.amount.equalsIgnoreCase("N/A") ? "" : currentItem.amount + " ";
                    detailsText += "due on " + dateString;
                    textViewDetails.setText(detailsText.trim());

                    typeBackground.setColor(ContextCompat.getColor(itemView.getContext(), R.color.md_theme_primary));
                } else {
                    // Cancelled Reminder
                    textViewDetails.setVisibility(View.VISIBLE);
                    textViewDetails.setText("Cancelled");
                    typeBackground.setColor(ContextCompat.getColor(itemView.getContext(), R.color.md_theme_outline));
                }
            } else { // "NOTE"
                textViewDetails.setVisibility(View.GONE);
                typeBackground.setColor(ContextCompat.getColor(itemView.getContext(), R.color.md_theme_secondary));
            }
        }
    }

    // --- Interfaces for click handling ---

    public interface OnItemDeleteListener {
        void onItemDelete(ReminderItem item);
    }

    public void setOnItemDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    public interface OnItemClickListener {
        void onItemClick(ReminderItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }
}

// DiffUtil automatically calculates list changes for smooth animations
class ReminderDiffCallback extends DiffUtil.ItemCallback<ReminderItem> {
    @Override
    public boolean areItemsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
        return oldItem.id == newItem.id;
    }

    @Override
    public boolean areContentsTheSame(@NonNull ReminderItem oldItem, @NonNull ReminderItem newItem) {
        return oldItem.content.equals(newItem.content) &&
                oldItem.isActive == newItem.isActive &&
                oldItem.reminderTime == newItem.reminderTime &&
                oldItem.type.equals(newItem.type);
    }
}