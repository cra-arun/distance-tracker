package com.example.distance_track;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AchievementsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_achievements, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fetchDistanceBadgesFromFirebase(view.findViewById(R.id.recycler_distance));
        fetchDaysBadgesFromFirebase(view.findViewById(R.id.recycler_days));

        setupRecycler(view.findViewById(R.id.recycler_calories), getCaloriesBadges());
        setupRecycler(view.findViewById(R.id.recycler_time), getTimeBadges());
        setupRecycler(view.findViewById(R.id.recycler_yearly), getYearBadges());
    }

    private void setupRecycler(RecyclerView recyclerView, List<Badgemodel> data) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(new BadgeAdapter(data));
    }

    private void fetchDistanceBadgesFromFirebase(RecyclerView recyclerView) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/").getReference("Users")
                .child(userId)
                .child("DistancePerDay");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalDistance = 0.0;
                for (DataSnapshot day : snapshot.getChildren()) {
                    Double value = day.getValue(Double.class);
                    if (value != null) totalDistance += value;
                }

                List<Badgemodel> list = new ArrayList<>();
                int[] thresholds = {10, 20, 50, 100, 200, 500, 1000};
                int[] images = {
                        R.drawable.km_10, R.drawable.km_20, R.drawable.km_50,
                        R.drawable.km_100, R.drawable.km_200, R.drawable.km_500, R.drawable.km_1000
                };

                for (int i = 0; i < thresholds.length; i++) {
                    int progress = Math.min(100, (int) ((totalDistance / thresholds[i]) * 100));
                    boolean unlocked = progress >= 100;
                    list.add(new Badgemodel(
                            thresholds[i] + " KM",
                            R.drawable.badge_locked,
                            images[i],
                            unlocked,
                            progress
                    ));
                }

                setupRecycler(recyclerView, list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void fetchDaysBadgesFromFirebase(RecyclerView recyclerView) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/").getReference("Users")
                .child(userId)
                .child("DistancePerDay");


        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int activeDays = (int) snapshot.getChildrenCount();

                List<Badgemodel> list = new ArrayList<>();
                int[] dayMilestones = {1, 7, 14, 30, 60, 90, 120, 150, 300};
                int icon = R.drawable.km_10; // Replace with actual images if you have per milestone

                for (int days : dayMilestones) {
                    int progress = Math.min(100, (int) ((activeDays * 100.0f) / days));
                    boolean unlocked = progress >= 100;

                    list.add(new Badgemodel(
                            days + " Days",
                            R.drawable.badge_locked,
                            icon,
                            unlocked,
                            progress
                    ));
                }

                setupRecycler(recyclerView, list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private List<Badgemodel> getCaloriesBadges() {
        List<Badgemodel> list = new ArrayList<>();
        list.add(new Badgemodel("100 Cal", R.drawable.badge_locked, R.drawable.cal_100, true, 100));
        list.add(new Badgemodel("500 Cal", R.drawable.badge_locked, R.drawable.cal_500, true, 100));
        list.add(new Badgemodel("1000 Cal", R.drawable.badge_locked, R.drawable.cal_1000, true, 100));
        list.add(new Badgemodel("5000 Cal", R.drawable.badge_locked, R.drawable.cal_5000, true, 100));
        list.add(new Badgemodel("10000 Cal", R.drawable.badge_locked, R.drawable.cal_10000, true, 100));
        return list;
    }

    private List<Badgemodel> getTimeBadges() {
        List<Badgemodel> list = new ArrayList<>();
        list.add(new Badgemodel("7 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("14 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("30 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("60 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("90 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("180 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        list.add(new Badgemodel("300 Min", R.drawable.badge_locked, R.drawable.km_10, true, 100));
        return list;
    }

    private List<Badgemodel> getYearBadges() {
        List<Badgemodel> list = new ArrayList<>();
        list.add(new Badgemodel("1 Year", R.drawable.badge_locked, R.drawable.km_10, false, 10));
        return list;
    }
}
