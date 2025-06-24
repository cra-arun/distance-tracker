package com.example.distance_track;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<HistoryItem> historyList;

    public HistoryAdapter(List<HistoryItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);
        holder.dateText.setText(item.getDate());
        holder.distanceText.setText(String.format("%.2f km", item.getDistance()));
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView dateText, distanceText;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.textDate);
            distanceText = itemView.findViewById(R.id.textDistance);
        }
    }
}
