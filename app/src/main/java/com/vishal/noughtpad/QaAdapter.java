package com.vishal.noughtpad;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class QaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final List<QaMessage> messageList;

    public QaAdapter(List<QaMessage> messageList) {
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        if (QaMessage.SENT_BY_USER.equals(messageList.get(position).sentBy)) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_AI;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_qa_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_qa_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        QaMessage message = messageList.get(position);
        if (holder.getItemViewType() == VIEW_TYPE_USER) {
            ((UserViewHolder) holder).userMessage.setText(message.message);
        } else {
            ((AiViewHolder) holder).aiMessage.setText(message.message);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userMessage;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userMessage = itemView.findViewById(R.id.user_message);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView aiMessage;
        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            aiMessage = itemView.findViewById(R.id.ai_message);
        }
    }
}