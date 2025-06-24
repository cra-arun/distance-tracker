package com.example.distance_track;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder> {

    private List<Badgemodel> badgeList;

    public BadgeAdapter(List<Badgemodel> badgeList) {
        this.badgeList = badgeList;
    }

    @NonNull
    @Override
    public BadgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_badge, parent, false);
        return new BadgeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BadgeViewHolder holder, int position) {
        Badgemodel badge = badgeList.get(position);
        holder.title.setText(badge.getTitle());

        if (badge.isUnlocked()) {
            holder.image.setImageResource(badge.getUnlockedDrawable());
        } else {
            holder.image.setImageResource(R.drawable.badge_locked); // ← use your lock vector drawable
        }

        holder.progressBar.setProgress(badge.getProgress());
        holder.percentage.setText(badge.getProgress() + "%");
    }

    @Override
    public int getItemCount() {
        return badgeList.size();
    }

    static class BadgeViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, percentage;
        ProgressBar progressBar;

        public BadgeViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.badge_image);
            title = itemView.findViewById(R.id.badge_title);
            percentage = itemView.findViewById(R.id.textProgress);
            progressBar = itemView.findViewById(R.id.badge_progress);
        }
    }
}
