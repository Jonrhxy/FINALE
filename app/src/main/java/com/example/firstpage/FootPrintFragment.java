package com.example.firstpage;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class FootPrintFragment extends Fragment {

    // UI references
    private TextView helloText, emissionText, noDataText, detailsDescription;
    private TextView emittedValue, reduceValue;
    private TextView foodValue, transportValue;
    private View foodBarFill, transportBarFill;
    private PieChart pieChart;
    private BarChart barChart;
    private RadioGroup radioGroupPeriod;
    // New container for day labels
    private LinearLayout dayLabelsContainer;

    // Keep a reference to the inflated view
    private View rootView;

    // Color constants
    private static final String DARK_GREEN = "#2E7D32";
    private static final String LIGHT_GREEN = "#81C784";

    // Gauge thresholds
    private static final String GREEN = "#4CAF50";   // HAPPY
    private static final String YELLOW = "#FFC107";  // POKER
    private static final String RED = "#F44336";     // SAD

    // We'll store these after fetching from Firestore
    private double transportEmission = 0;
    private double foodEmission = 0;

    public FootPrintFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Save the inflated view to a member variable
        rootView = inflater.inflate(R.layout.activity_foot_print_fragment, container, false);

        // 1. Initialize all Views using rootView
        helloText = rootView.findViewById(R.id.helloText);
        emissionText = rootView.findViewById(R.id.emissionText);
        noDataText = rootView.findViewById(R.id.noDataText);
        detailsDescription = rootView.findViewById(R.id.detailsDescription);

        emittedValue = rootView.findViewById(R.id.emittedValue);
        reduceValue = rootView.findViewById(R.id.reduceValue);

        foodValue = rootView.findViewById(R.id.foodValue);
        transportValue = rootView.findViewById(R.id.transportValue);
        foodBarFill = rootView.findViewById(R.id.foodBarFill);
        transportBarFill = rootView.findViewById(R.id.transportBarFill);

        pieChart = rootView.findViewById(R.id.pieChart);
        barChart = rootView.findViewById(R.id.barChart);
        radioGroupPeriod = rootView.findViewById(R.id.radioGroupPeriod);
        // New: initialize the day labels container
        dayLabelsContainer = rootView.findViewById(R.id.dayLabelsContainer);

        // 2. Fetch user data for greeting and daily emission
        fetchUserData();
        fetchTodayEmission();
        // For demonstration, set "Reduce CO2" statically
        reduceValue.setText("30");

        // 3. Style the PieChart as a half-donut gauge
        styleGauge(pieChart);

        // 4. Fetch Firestore data for the gauge (transportation + food)
        fetchCarbonFootprintData();

        // 5. Setup Week/Month toggle for the BarChart and update day labels accordingly
        radioGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            fetchBarGraphData();
            updateDayLabels();
        });
        // By default, fetch data for the BarChart and update day labels
        fetchBarGraphData();
        updateDayLabels();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear the reference to avoid updating a null view
        rootView = null;
    }

    /**
     * Fetch user's name from FirebaseAuth/Firestore for greeting.
     */
    private void fetchUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            helloText.setText("Hello, Guest");
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            helloText.setText("Hello, " + displayName);
        } else {
            String userId = user.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(userId).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot doc = task.getResult();
                            if (doc != null && doc.exists()) {
                                String firstName = doc.getString("firstName");
                                String lastName = doc.getString("lastName");
                                if (firstName == null && lastName == null) {
                                    helloText.setText("Hello, User");
                                } else {
                                    String greeting = "Hello, " +
                                            (firstName != null ? firstName : "") + " " +
                                            (lastName != null ? lastName : "");
                                    helloText.setText(greeting.trim());
                                }
                            } else {
                                helloText.setText("Hello, User");
                            }
                        } else {
                            helloText.setText("Failed to load user data");
                        }
                    })
                    .addOnFailureListener(e -> helloText.setText("Error retrieving data"));
        }
    }

    /**
     * Example method to set daily emission text (and update top "Emitted CO2" box).
     */
    private void fetchTodayEmission() {
        double dailyCO2 = 120.0; // Hardcoded example
        emissionText.setText("You have emitted " + dailyCO2 + " kg CO2 this day");
        emittedValue.setText(String.valueOf(dailyCO2));
    }

    /**
     * Style the PieChart to look like a half-donut gauge.
     */
    private void styleGauge(PieChart chart) {
        chart.setMaxAngle(180f);         // Only 180° visible
        chart.setRotationAngle(180f);    // Flat edge at bottom
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(70f);
        chart.setTransparentCircleRadius(0f);
        chart.setDrawSlicesUnderHole(false);
        chart.setDrawRoundedSlices(false);
        chart.setDrawCenterText(true);
        chart.setCenterTextSize(14f);
        chart.setCenterTextColor(Color.BLACK);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
    }

    /**
     * Fetch carbon footprint data (transportation + food) from Firestore,
     * then update the gauge with a SINGLE SLICE for the total.
     */
    private void fetchCarbonFootprintData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String username = user.getDisplayName();
        if (username == null || username.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<DocumentSnapshot> transTask = db.collection("transportation").document(username).get();
        Task<DocumentSnapshot> foodTask = db.collection("food_sources").document(username).get();

        Tasks.whenAllSuccess(transTask, foodTask)
                .addOnSuccessListener(tasks -> {
                    // Check if fragment is still attached before updating UI
                    if (!isAdded()) return;

                    double tVal = 0.0;
                    double fVal = 0.0;
                    if (tasks.size() >= 2) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) tasks.get(0);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) tasks.get(1);

                        if (transDoc != null && transDoc.exists()) {
                            Double parsedT = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (parsedT != null) tVal = parsedT;
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double parsedF = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (parsedF != null) fVal = parsedF;
                        }
                    }
                    transportEmission = tVal;
                    foodEmission = fVal;
                    Log.d("FootPrintFragment", "Gauge Data - Transport: " + transportEmission + ", Food: " + foodEmission);

                    // SINGLE SLICE -> sum the two
                    double totalEmission = transportEmission + foodEmission;
                    updateGauge(totalEmission);

                    // Also update the bottom bars with the separate values
                    updateHorizontalBars(transportEmission, foodEmission);
                })
                .addOnFailureListener(e -> Log.e("FootPrintFragment", "Error fetching gauge data", e));
    }

    /**
     * Update the half-donut gauge (PieChart) with a SINGLE slice for total emission.
     *
     * Thresholds:
     *   - < 1 => HAPPY (green)
     *   - [1, 5) => POKER (yellow)
     *   - >= 5 => SAD (red)
     */
    private void updateGauge(double totalEmission) {
        Log.d("FootPrintFragment", "Total Emission: " + totalEmission);

        int gaugeColor;
        int emotionDrawable;

        if (totalEmission < 1) {
            gaugeColor = Color.parseColor(GREEN);
            emotionDrawable = R.drawable.happy_face;
        } else if (totalEmission < 5) {
            gaugeColor = Color.parseColor(YELLOW);
            emotionDrawable = R.drawable.poker_face;
        } else {
            gaugeColor = Color.parseColor(RED);
            emotionDrawable = R.drawable.sad_face;
        }

        // Update PieChart
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry((float) totalEmission, ""));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColor(gaugeColor);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);
        dataSet.setSliceSpace(2f);

        PieData pieData = new PieData(dataSet);
        pieChart.setData(pieData);
        pieChart.invalidate();

        // Instead of using getView(), use rootView safely
        if (rootView != null) {
            ImageView emotionImage = rootView.findViewById(R.id.emotionImage);
            if (emotionImage != null) {
                emotionImage.setImageResource(emotionDrawable);
            }
        }
    }

    /**
     * Fetch BarChart data from Firestore and update the bar chart + bottom bars.
     */
    private void fetchBarGraphData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String username = user.getDisplayName();
        if (username == null || username.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<DocumentSnapshot> transTask = db.collection("transportation").document(username).get();
        Task<DocumentSnapshot> foodTask = db.collection("food_sources").document(username).get();

        Tasks.whenAllSuccess(transTask, foodTask)
                .addOnSuccessListener(tasks -> {
                    // Check if fragment is still attached
                    if (!isAdded()) return;

                    double transportVal = 0;
                    double foodVal = 0;
                    if (tasks.size() >= 2) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) tasks.get(0);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) tasks.get(1);

                        if (transDoc != null && transDoc.exists()) {
                            Double cf = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (cf != null) transportVal = cf;
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double cf = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (cf != null) foodVal = cf;
                        }
                    }
                    Log.d("FootPrintFragment", "BarChart Data - Transport: " + transportVal + ", Food: " + foodVal);
                    if (transportVal == 0 && foodVal == 0) {
                        noDataText.setVisibility(View.VISIBLE);
                        barChart.setVisibility(View.GONE);
                    } else {
                        noDataText.setVisibility(View.GONE);
                        barChart.setVisibility(View.VISIBLE);
                        updateBarChart(transportVal, foodVal);
                    }
                    // Also update bottom bars
                    updateHorizontalBars(transportVal, foodVal);
                })
                .addOnFailureListener(e -> {
                    Log.e("FootPrintFragment", "Error fetching bar chart data", e);
                    if (rootView != null) {
                        noDataText.setVisibility(View.VISIBLE);
                        barChart.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Update the BarChart with transport and food values.
     */
    private void updateBarChart(double transport, double food) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) transport)); // X=0 for Transport
        entries.add(new BarEntry(1f, (float) food));      // X=1 for Food

        BarDataSet dataSet = new BarDataSet(entries, "Carbon Footprint");
        dataSet.setColors(Color.parseColor(DARK_GREEN), Color.parseColor(LIGHT_GREEN));
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        barChart.setData(barData);

        String[] labels = {"Transport", "Food"};
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        barChart.getDescription().setEnabled(false);
        barChart.invalidate();
    }

    /**
     * Dynamically fill the bottom horizontal bars (Food, Transport).
     * Note: This method checks if the fragment is still attached
     * to avoid IllegalStateException when calling getResources().
     */
    private void updateHorizontalBars(double transport, double food) {
        if (!isAdded() || rootView == null) return;

        double maxCO2 = 100.0; // define a maximum for the bars

        float foodFraction = (float) Math.min(food / maxCO2, 1.0);
        float transportFraction = (float) Math.min(transport / maxCO2, 1.0);

        Log.d("FootPrintFragment", "updateHorizontalBars -> Transport: " + transport + ", Food: " + food +
                ", foodFraction=" + foodFraction + ", transportFraction=" + transportFraction);

        // Update text
        foodValue.setText(food + " CO2");
        transportValue.setText(transport + " CO2");

        // Convert dp to px using rootView's resources
        float totalBarWidthDp = 80f;
        float scale = rootView.getResources().getDisplayMetrics().density;
        int foodFillPx = (int) (foodFraction * totalBarWidthDp * scale);
        int transportFillPx = (int) (transportFraction * totalBarWidthDp * scale);

        // Food bar fill
        ViewGroup.LayoutParams foodParams = foodBarFill.getLayoutParams();
        foodParams.width = foodFillPx;
        foodBarFill.setLayoutParams(foodParams);

        // Transport bar fill
        ViewGroup.LayoutParams transportParams = transportBarFill.getLayoutParams();
        transportParams.width = transportFillPx;
        transportBarFill.setLayoutParams(transportParams);
    }

    /**
     * Updates the day (or week) labels below the bar chart.
     * For the week view, shows "Mon" to "Sun".
     * For the month view, shows week numbers (e.g. "Week 1"–"Week 4").
     */
    private void updateDayLabels() {
        if (dayLabelsContainer == null || !isAdded()) return;
        dayLabelsContainer.removeAllViews();

        int selectedId = radioGroupPeriod.getCheckedRadioButtonId();
        if (selectedId == R.id.radioWeek) {
            // For week view, create 7 labels for Monday to Sunday.
            dayLabelsContainer.setWeightSum(7);
            String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            for (String day : weekDays) {
                TextView tv = new TextView(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                tv.setLayoutParams(params);
                tv.setText(day);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                dayLabelsContainer.addView(tv);
            }
        } else {
            // For month view, assume 4 weeks (adjust as needed).
            dayLabelsContainer.setWeightSum(4);
            String[] weeks = {"Week 1", "Week 2", "Week 3", "Week 4"};
            for (String week : weeks) {
                TextView tv = new TextView(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                tv.setLayoutParams(params);
                tv.setText(week);
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                dayLabelsContainer.addView(tv);
            }
        }
    }

    /**
     * Safely parse a String into a Double, removing non-digit/decimal characters.
     */
    private Double parseDouble(String value) {
        Log.d("parseDouble", "Original: [" + value + "]");
        if (value == null || !value.matches(".*\\d.*")) return 0.0;
        try {
            String numeric = value.replaceAll("[^\\d.]", "");
            Log.d("parseDouble", "Numeric only: [" + numeric + "]");
            return Double.parseDouble(numeric);
        } catch (NumberFormatException e) {
            Log.e("parseDouble", "NumberFormatException parsing: " + value, e);
            return 0.0;
        }
    }
}
