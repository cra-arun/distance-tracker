package com.example.distance_track;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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

public class fragment_history extends Fragment {

    private HistoryAdapter adapter;
    private final List<HistoryItem> historyList = new ArrayList<>();
    private DatabaseReference databaseRef;

    public fragment_history() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fragement_history, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        databaseRef = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/")
                .getReference("Users")
                .child(userId)
                .child("DistancePerDay");

        loadHistory();
        return view;
    }

    private void loadHistory() {
        historyList.clear();

        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    String date = dateSnapshot.getKey();
                    Double distance = dateSnapshot.getValue(Double.class);
                    if (date != null && distance != null) {
                        historyList.add(new HistoryItem(date, distance));
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Optionally log or show error
            }
        });
    }
}
