package com.example.distance_track;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class fragement_unit_select extends Fragment {
    private static final String TAG = "UnitSelectFragment";

    private BarChart barChart;
    private DatabaseReference databaseRef;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fragement_unit_select, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button cmBtn = view.findViewById(R.id.cmBtn);
        Button mBtn = view.findViewById(R.id.mBtn);
        Button kmBtn = view.findViewById(R.id.kmBtn);
        barChart = view.findViewById(R.id.barChart);

        View.OnClickListener listener = v -> {
            String unit = "";
            if (v.getId() == R.id.cmBtn) unit = "cm";
            else if (v.getId() == R.id.mBtn) unit = "m";
            else if (v.getId() == R.id.kmBtn) unit = "km";

            fragement_distance distanceFragment = new fragement_distance();
            Bundle bundle = new Bundle();
            bundle.putString("unit", unit);
            distanceFragment.setArguments(bundle);
            FragmentTransaction transaction = requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction();

            // Optional: Add transition animation
            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);

            // Replace with correct container ID and fragment
            transaction.replace(R.id.nav_host_fragment_content_main, distanceFragment);  // ✅ Correct
            transaction.addToBackStack(null);
            transaction.commit();

        };

        cmBtn.setOnClickListener(listener);
        mBtn.setOnClickListener(listener);
        kmBtn.setOnClickListener(listener);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Initialize Firebase database reference
        databaseRef = FirebaseDatabase.getInstance("https://payment11-4e916-default-rtdb.firebaseio.com/")
                .getReference("Users")
                .child(userId)
                .child("DistancePerDay");
        loadBarGraphData();
    }

    private void loadBarGraphData() {
        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "No walking data found.", Toast.LENGTH_SHORT).show();
                    barChart.clear();
                    barChart.invalidate();
                    return;
                }

                Map<String, Float> sortedData = new TreeMap<>();
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    String date = dateSnapshot.getKey();
                    Float value = null;
                    try {
                        Object valObj = dateSnapshot.getValue();
                        if (valObj instanceof Double) {
                            value = ((Double) valObj).floatValue();
                        } else if (valObj instanceof Long) {
                            value = ((Long) valObj).floatValue();
                        } else if (valObj instanceof Float) {
                            value = (Float) valObj;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Value parsing error for date " + date, e);
                    }
                    sortedData.put(date, value != null ? value : 0f);
                }

                if (sortedData.isEmpty()) {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "No walking data available to display.", Toast.LENGTH_SHORT).show();
                    barChart.clear();
                    barChart.invalidate();
                    return;
                }

                displayGraph(new ArrayList<>(sortedData.keySet()), new ArrayList<>(sortedData.values()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read Firebase data", error.toException());
                if (getContext() != null)
                    Toast.makeText(getContext(), "Failed to load data from Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayGraph(List<String> dates, List<Float> distances) {
        List<BarEntry> entries = new ArrayList<>();
        // Assuming 'barChart' is your BarChart object
// Assuming 'dataSet' is your BarDataSet object (part of your BarData)

        if (getContext() != null && barChart != null) {
            int themedTextColor = getThemedTextColor(requireContext());

            // X-Axis labels
            barChart.getXAxis().setTextColor(themedTextColor);

            // Y-Axis (Left) labels
            barChart.getAxisLeft().setTextColor(themedTextColor);

            // Y-Axis (Right) labels (if enabled)
            if (barChart.getAxisRight().isEnabled()) {
                barChart.getAxisRight().setTextColor(themedTextColor);
            }

            // Data set value labels (text on top of bars)
            // If you have multiple datasets, loop through them:
            // for (IBarDataSet iSet : barChart.getData().getDataSets()) {
            //    iSet.setValueTextColor(themedTextColor);
            // }
            // If you have a single dataset:
            if (barChart.getData() != null && barChart.getData().getDataSetCount() > 0) {
                // Assuming your first dataset is the one you want to style
                barChart.getData().getDataSetByIndex(0).setValueTextColor(themedTextColor);
            }


            // Legend text
            barChart.getLegend().setTextColor(themedTextColor);

            // Description text (if enabled)
            if (barChart.getDescription().isEnabled()) {
                barChart.getDescription().setTextColor(themedTextColor);
            }

            // "No Data" / Info text (for when the chart is empty)
            barChart.getPaint(BarChart.PAINT_INFO).setColor(themedTextColor);

            // After changing colors, you might need to refresh the chart
            // barChart.invalidate(); // Call this after all chart setup is done
        }
        int size = distances.size();
        int startIndex = Math.max(0, size - 7);
        List<String> last7Dates = dates.subList(startIndex, size);
        List<Float> last7Values = distances.subList(startIndex, size);
        for (int i = 0; i < last7Values.size(); i++) {
            entries.add(new BarEntry(i, last7Values.get(i)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Distance in KM");
        dataSet.setColor(getResources().getColor(R.color.teal_700, null));
        BarData data = new BarData(dataSet);
        data.setBarWidth(0.05f);
        barChart.setData(data);
        barChart.setFitBars(true);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(last7Dates));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45);

        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getDescription().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate();
    }
    private int getThemedTextColor(@NonNull Context context) {
        // Option 1: Use your custom theme attribute 'chartTextColor'
        // Ensure 'chartTextColor' is defined in your themes.xml and themes.xml (night)
        // TypedValue typedValue = new TypedValue();
        // if (context.getTheme().resolveAttribute(R.attr.chartTextColor, typedValue, true)) {
        //     return typedValue.data;
        // }

        // Option 2: Use a standard Android theme attribute like 'textColorPrimary'
        // This is often a good general choice for text.
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
            return typedValue.data;
        }

        // Fallback if the attribute isn't found (should not happen with standard attributes)
        Log.w("ChartTheming", "Text color theme attribute not found. Defaulting to black.");
        return Color.BLACK;
    }
}

