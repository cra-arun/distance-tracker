package com.example.distance_track;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class fragment_map extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference pathPointsRef;
    private String todayDate;
    private Button selectDateButton;
    private String userId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        pathPointsRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId)
                .child("PathPoints").child(todayDate);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        selectDateButton = view.findViewById(R.id.selectDateButton);
        selectDateButton.setOnClickListener(v -> showDateSelectionPopup());

        return view;
    }

    private void showDateSelectionPopup() {
        DatabaseReference userPathPointsRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userId).child("PathPoints");

        userPathPointsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<String> dateList = new ArrayList<>();
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    dateList.add(dateSnapshot.getKey());
                }
                Collections.sort(dateList, Collections.reverseOrder());
                showDateDialog(dateList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to fetch dates", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDateDialog(ArrayList<String> dates) {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_date_list);

        ListView listView = dialog.findViewById(R.id.dateListView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dates);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            String selectedDate = dates.get(position);
            dialog.dismiss();
            loadPathForDate(selectedDate);
        });

        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void loadPathForDate(String date) {
        DatabaseReference selectedDateRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userId).child("PathPoints").child(date);

        selectedDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap != null) {
                    mMap.clear();
                    ArrayList<LatLng> pathPoints = new ArrayList<>();
                    for (DataSnapshot pointSnapshot : snapshot.getChildren()) {
                        Double lat = pointSnapshot.child("lat").getValue(Double.class);
                        Double lng = pointSnapshot.child("lng").getValue(Double.class);
                        if (lat != null && lng != null) {
                            pathPoints.add(new LatLng(lat, lng));
                        }
                    }
                    if (!pathPoints.isEmpty()) {
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(pathPoints)
                                .width(8)
                                .color(0xFF007AFF);
                        mMap.addPolyline(polylineOptions);
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.get(0), 15f));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load path", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        loadPathForDate(todayDate);
    }
}
