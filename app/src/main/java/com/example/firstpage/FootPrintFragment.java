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
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FootPrintFragment extends Fragment {

    private TextView helloText, emissionText, noDataText, detailsDescription;
    private TextView emittedValue, reduceValue;
    private TextView foodValue, transportValue;
    private View foodBarFill, transportBarFill;
    private PieChart pieChart;
    private BarChart barChart;
    private RadioGroup radioGroupPeriod;
    private LinearLayout dayLabelsContainer;
    private View rootView;

    // Color constants
    private static final String DARK_GREEN = "#2E7D32";
    private static final String LIGHT_GREEN = "#81C784";

    // Gauge threshold colors
    private static final String GREEN = "#4CAF50";
    private static final String YELLOW = "#FFC107";
    private static final String RED = "#F44336";

    // Stored emission values (raw values from Firestore)
    private double transportEmission = 0;
    private double foodEmission = 0;

    private static final String TAG = "FootPrintFragment";

    // Server-based date (to ensure server time is used for queries)
    private Date serverDateObject = null;

    public FootPrintFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.activity_foot_print_fragment, container, false);

        // Initialize UI components.
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
        dayLabelsContainer = rootView.findViewById(R.id.dayLabelsContainer);

        fetchUserData();
        // Initialize reduceValue to zero; it will update when Firestore is fetched.
        reduceValue.setText("0.0");

        styleGauge(pieChart);

        // Get the server date from Firestore so that queries use the server's year.
        getServerDateFromFirestore().addOnSuccessListener(date -> {
            if (!isAdded() || rootView == null) return;
            serverDateObject = date;
            fetchTodayEmission();
            fetchCarbonFootprintData();
            setupRadioGroup();
            RadioButton rbToday = rootView.findViewById(R.id.radioToday);
            rbToday.setChecked(true);
            fetchSingleDayBarGraphData();
            updateDayLabels();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching server date; falling back to device time", e);
            if (!isAdded() || rootView == null) return;
            serverDateObject = new Date();
            fetchTodayEmission();
            fetchCarbonFootprintData();
            setupRadioGroup();
            RadioButton rbToday = rootView.findViewById(R.id.radioToday);
            rbToday.setChecked(true);
            fetchSingleDayBarGraphData();
            updateDayLabels();
        });

        return rootView;
    }

    /**
     * Fetch and display the user's name.
     */
    private void fetchUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (rootView != null) helloText.setText("Hello, Guest");
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            if (rootView != null) helloText.setText("Hello, " + displayName);
        } else {
            if (rootView != null) helloText.setText("Hello, User");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null; // Prevent memory leaks.
    }

    /**
     * Sets up the radio group listeners to fetch data for Today, Week, or Month.
     */
    private void setupRadioGroup() {
        if (!isAdded() || rootView == null) return;
        radioGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (!isAdded() || rootView == null) return;
            if (checkedId == R.id.radioToday) {
                fetchSingleDayBarGraphData();
            } else if (checkedId == R.id.radioWeek) {
                fetchWeeklyBarGraphData();
            } else if (checkedId == R.id.radioMonth) {
                fetchMonthlyBarGraphData();
            }
            updateDayLabels();
        });
    }

    /**
     * Uses Firestore to get the server time (via a dummy document write/read).
     */
    private Task<Date> getServerDateFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("server_time").document("dummy");
        return docRef.set(Collections.singletonMap("timestamp", FieldValue.serverTimestamp()))
                .continueWithTask(task -> docRef.get())
                .continueWith(task -> {
                    DocumentSnapshot snapshot = task.getResult();
                    if (snapshot != null && snapshot.exists() && snapshot.get("timestamp") != null) {
                        Timestamp ts = snapshot.getTimestamp("timestamp");
                        return ts.toDate();
                    }
                    throw new Exception("Failed to retrieve server timestamp.");
                });
    }

    /**
     * Helper method to build a query date string using the server's year.
     */
    private String buildQueryDate(Date selectedDate) {
        if (serverDateObject == null || selectedDate == null) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate);
        }
        SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy", Locale.getDefault());
        SimpleDateFormat sdfMonthDay = new SimpleDateFormat("MM-dd", Locale.getDefault());
        String serverYear = sdfYear.format(serverDateObject);
        String monthDay = sdfMonthDay.format(selectedDate);
        return serverYear + "-" + monthDay;
    }

    /**
     * Fetch today's emission data from Firestore and calculate net emission.
     * Net emission = (transportation emission - reduction) + food emission.
     */
    private void fetchTodayEmission() {
        if (serverDateObject == null) serverDateObject = new Date();
        String today = buildQueryDate(serverDateObject);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (rootView != null) {
                emissionText.setText("You have emitted 0.0 kg CO2 this day");
                emittedValue.setText("0.0");
                reduceValue.setText("0.0");
            }
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            if (rootView != null) {
                emissionText.setText("You have emitted 0.0 kg CO2 this day");
                emittedValue.setText("0.0");
                reduceValue.setText("0.0");
            }
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<DocumentSnapshot> transTask = db.collection("transportation")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();
        Task<DocumentSnapshot> foodTask = db.collection("food_sources")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();

        Tasks.whenAllSuccess(transTask, foodTask)
                .addOnSuccessListener(tasks -> {
                    if (!isAdded() || rootView == null) return;
                    double transportVal = 0.0, foodVal = 0.0, reduction = 0.0;
                    if (tasks.size() >= 2) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) tasks.get(0);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) tasks.get(1);
                        if (transDoc != null && transDoc.exists()) {
                            Double tVal = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (tVal != null) transportVal = tVal;
                            String reductionStr = transDoc.getString("total_carbon_reduced");
                            if (reductionStr != null && !reductionStr.isEmpty()) {
                                reduction = Double.parseDouble(reductionStr);
                                reduceValue.setText(String.format(Locale.getDefault(), "%.2f", reduction));
                            } else {
                                reduceValue.setText("0.0");
                            }
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double fVal = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (fVal != null) foodVal = fVal;
                        }
                    }
                    // Calculate net transportation emission.
                    double netTransport = transportVal - reduction;
                    double dailyCO2 = netTransport + foodVal;
                    emissionText.setText(String.format(Locale.getDefault(),
                            "You have emitted %.2f kg CO2 this day", dailyCO2));
                    emittedValue.setText(String.format(Locale.getDefault(), "%.2f", dailyCO2));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || rootView == null) return;
                    Log.e(TAG, "Error fetching daily emission", e);
                    emissionText.setText("You have emitted 0.0 kg CO2 this day");
                    emittedValue.setText("0.0");
                    reduceValue.setText("0.0");
                });
    }

    /**
     * Style the PieChart gauge.
     */
    private void styleGauge(PieChart chart) {
        chart.setMaxAngle(180f);
        chart.setRotationAngle(180f);
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
     * Fetch single-day gauge data and update the gauge.
     */
    private void fetchCarbonFootprintData() {
        if (serverDateObject == null) return;
        String today = buildQueryDate(serverDateObject);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<DocumentSnapshot> transTask = db.collection("transportation")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();
        Task<DocumentSnapshot> foodTask = db.collection("food_sources")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();

        Tasks.whenAllSuccess(transTask, foodTask)
                .addOnSuccessListener(tasks -> {
                    if (!isAdded() || rootView == null) return;
                    double tVal = 0.0, fVal = 0.0, reduction = 0.0;
                    if (tasks.size() >= 2) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) tasks.get(0);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) tasks.get(1);
                        if (transDoc != null && transDoc.exists()) {
                            Double parsedT = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (parsedT != null) tVal = parsedT;
                            String reductionStr = transDoc.getString("total_carbon_reduced");
                            if (reductionStr != null && !reductionStr.isEmpty()) {
                                reduction = Double.parseDouble(reductionStr);
                            }
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double parsedF = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (parsedF != null) fVal = parsedF;
                        }
                    }
                    double netTransport = tVal - reduction;
                    double total = netTransport + fVal;
                    updateGauge(total);
                    updateHorizontalBars(netTransport, fVal);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || rootView == null) return;
                    Log.e(TAG, "Error fetching gauge data", e);
                });
    }

    /**
     * Fetch single-day bar chart data, calculating net transportation emission,
     * and update the bar chart.
     */
    private void fetchSingleDayBarGraphData() {
        if (serverDateObject == null) return;
        String today = buildQueryDate(serverDateObject);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<DocumentSnapshot> transTask = db.collection("transportation")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();
        Task<DocumentSnapshot> foodTask = db.collection("food_sources")
                .document(displayName)
                .collection("daily")
                .document(today)
                .get();

        Tasks.whenAllSuccess(transTask, foodTask)
                .addOnSuccessListener(tasks -> {
                    if (!isAdded() || rootView == null) return;
                    double transportVal = 0;
                    double foodVal = 0;
                    double reduction = 0;
                    if (tasks.size() >= 2) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) tasks.get(0);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) tasks.get(1);
                        if (transDoc != null && transDoc.exists()) {
                            Double cf = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (cf != null) transportVal = cf;
                            String reductionStr = transDoc.getString("total_carbon_reduced");
                            if (reductionStr != null && !reductionStr.isEmpty()) {
                                reduction = Double.parseDouble(reductionStr);
                            }
                            // Calculate net transport emission.
                            transportVal = transportVal - reduction;
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double cf = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (cf != null) foodVal = cf;
                        }
                    }
                    if (transportVal == 0 && foodVal == 0) {
                        noDataText.setVisibility(View.VISIBLE);
                        barChart.setVisibility(View.GONE);
                    } else {
                        noDataText.setVisibility(View.GONE);
                        barChart.setVisibility(View.VISIBLE);
                        updateBarChart(transportVal, foodVal);
                    }
                    updateHorizontalBars(transportVal, foodVal);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || rootView == null) return;
                    Log.e(TAG, "Error fetching single-day bar data", e);
                    noDataText.setVisibility(View.VISIBLE);
                    barChart.setVisibility(View.GONE);
                });
    }

    /**
     * Fetch weekly bar chart data.
     */
    private void fetchWeeklyBarGraphData() {
        if (serverDateObject == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> weekDates = getDatesForThisWeek();

        List<Task<DocumentSnapshot>> transTasks = new ArrayList<>();
        List<Task<DocumentSnapshot>> foodTasks = new ArrayList<>();
        for (String dateStr : weekDates) {
            Task<DocumentSnapshot> tTask = db.collection("transportation")
                    .document(displayName)
                    .collection("daily")
                    .document(dateStr)
                    .get();
            transTasks.add(tTask);
            Task<DocumentSnapshot> fTask = db.collection("food_sources")
                    .document(displayName)
                    .collection("daily")
                    .document(dateStr)
                    .get();
            foodTasks.add(fTask);
        }
        List<Task<?>> allTasks = new ArrayList<>();
        allTasks.addAll(transTasks);
        allTasks.addAll(foodTasks);

        Tasks.whenAllSuccess(allTasks)
                .addOnSuccessListener(results -> {
                    if (!isAdded() || rootView == null) return;
                    final String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
                    List<BarEntry> transportEntries = new ArrayList<>();
                    List<BarEntry> foodEntries = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        DocumentSnapshot transDoc = (DocumentSnapshot) results.get(i);
                        DocumentSnapshot foodDoc = (DocumentSnapshot) results.get(i + 7);
                        double transVal = 0.0;
                        double foodVal = 0.0;
                        double reduction = 0.0;
                        if (transDoc != null && transDoc.exists()) {
                            Double parsedT = parseDouble(transDoc.getString("total_carbon_footprint"));
                            if (parsedT != null) transVal = parsedT;
                            String reductionStr = transDoc.getString("total_carbon_reduced");
                            if (reductionStr != null && !reductionStr.isEmpty()) {
                                reduction = Double.parseDouble(reductionStr);
                            }
                            transVal = transVal - reduction;
                        }
                        if (foodDoc != null && foodDoc.exists()) {
                            Double parsedF = parseDouble(foodDoc.getString("total_carbon_footprint"));
                            if (parsedF != null) foodVal = parsedF;
                        }
                        transportEntries.add(new BarEntry(i, (float) transVal));
                        foodEntries.add(new BarEntry(i, (float) foodVal));
                    }
                    BarDataSet transportSet = new BarDataSet(transportEntries, "Transport");
                    transportSet.setColor(Color.parseColor(DARK_GREEN));
                    transportSet.setValueTextSize(14f);
                    transportSet.setValueTextColor(Color.BLACK);
                    transportSet.setValueFormatter(new ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            return (value == 0f) ? "" : String.format(Locale.getDefault(), "%.2f", value);
                        }
                    });
                    BarDataSet foodSet = new BarDataSet(foodEntries, "Food");
                    foodSet.setColor(Color.parseColor(LIGHT_GREEN));
                    foodSet.setValueTextSize(14f);
                    foodSet.setValueTextColor(Color.BLACK);
                    foodSet.setValueFormatter(new ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            return (value == 0f) ? "" : String.format(Locale.getDefault(), "%.2f", value);
                        }
                    });
                    BarData barData = new BarData(transportSet, foodSet);
                    float groupSpace = 0.25f;
                    float barSpace = 0f;
                    float barWidth = 0.375f;
                    barData.setBarWidth(barWidth);
                    barChart.setData(barData);
                    XAxis xAxis = barChart.getXAxis();
                    xAxis.setCenterAxisLabels(true);
                    xAxis.setGranularity(1f);
                    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                    xAxis.setDrawGridLines(false);
                    xAxis.setValueFormatter(new ValueFormatter() {
                        @Override
                        public String getAxisLabel(float value, AxisBase axis) {
                            int index = Math.round(value);
                            if (index >= 0 && index < dayLabels.length) {
                                return dayLabels[index];
                            }
                            return "";
                        }
                    });
                    float groupWidth = barData.getGroupWidth(groupSpace, barSpace);
                    barChart.getXAxis().setAxisMinimum(0f);
                    barChart.getXAxis().setAxisMaximum(groupWidth * dayLabels.length);
                    barChart.groupBars(0f, groupSpace, barSpace);
                    barChart.getDescription().setEnabled(false);
                    barChart.invalidate();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || rootView == null) return;
                    Log.e(TAG, "Error fetching weekly data", e);
                });
    }

    /**
     * Returns a list of date strings (yyyy-MM-dd) for the current week (Monday–Sunday),
     * using the server's year.
     */
    private List<String> getDatesForThisWeek() {
        List<String> dateList = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(serverDateObject);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        for (int i = 0; i < 7; i++) {
            Date d = cal.getTime();
            dateList.add(buildQueryDate(d));
            cal.add(Calendar.DATE, 1);
        }
        return dateList;
    }

    /**
     * Fetch monthly bar chart data.
     */
    private void fetchMonthlyBarGraphData() {
        if (serverDateObject == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<Double>> transportWeekSums = new ArrayList<>();
        List<Task<Double>> foodWeekSums = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            transportWeekSums.add(sumTransportForWeek(i, displayName));
            foodWeekSums.add(sumFoodForWeek(i, displayName));
        }
        List<Task<?>> allTasks = new ArrayList<>();
        allTasks.addAll(transportWeekSums);
        allTasks.addAll(foodWeekSums);
        Tasks.whenAllSuccess(allTasks).addOnSuccessListener(results -> {
            if (!isAdded() || rootView == null) return;
            final String[] weekLabels = {"Week1", "Week2", "Week3", "Week4"};
            List<BarEntry> transportEntries = new ArrayList<>();
            List<BarEntry> foodEntries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Double tVal = (Double) results.get(i);
                Double fVal = (Double) results.get(i + 4);
                double transVal = (tVal != null) ? tVal : 0.0;
                double foodVal = (fVal != null) ? fVal : 0.0;
                transportEntries.add(new BarEntry(i, (float) transVal));
                foodEntries.add(new BarEntry(i, (float) foodVal));
            }
            BarDataSet transportSet = new BarDataSet(transportEntries, "Transport");
            transportSet.setColor(Color.parseColor(DARK_GREEN));
            transportSet.setValueTextSize(14f);
            transportSet.setValueTextColor(Color.BLACK);
            transportSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return (value == 0f) ? "" : String.format(Locale.getDefault(), "%.2f", value);
                }
            });
            BarDataSet foodSet = new BarDataSet(foodEntries, "Food");
            foodSet.setColor(Color.parseColor(LIGHT_GREEN));
            foodSet.setValueTextSize(14f);
            foodSet.setValueTextColor(Color.BLACK);
            foodSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return (value == 0f) ? "" : String.format(Locale.getDefault(), "%.2f", value);
                }
            });
            BarData barData = new BarData(transportSet, foodSet);
            float groupSpace = 0.25f;
            float barSpace = 0f;
            float barWidth = 0.375f;
            barData.setBarWidth(barWidth);
            barChart.setData(barData);
            XAxis xAxis = barChart.getXAxis();
            xAxis.setCenterAxisLabels(true);
            xAxis.setGranularity(1f);
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    int index = Math.round(value);
                    if (index >= 0 && index < weekLabels.length) {
                        return weekLabels[index];
                    }
                    return "";
                }
            });
            float groupWidth = barData.getGroupWidth(groupSpace, barSpace);
            barChart.getXAxis().setAxisMinimum(0f);
            barChart.getXAxis().setAxisMaximum(groupWidth * weekLabels.length);
            barChart.groupBars(0f, groupSpace, barSpace);
            barChart.getDescription().setEnabled(false);
            barChart.invalidate();
        }).addOnFailureListener(e -> {
            if (!isAdded() || rootView == null) return;
            Log.e(TAG, "Error fetching monthly data", e);
        });
    }

    /**
     * Helper: Sum the "total_carbon_footprint" values for 7 days in a given week for a specified collection.
     */
    private Task<Double> sumDailyDocsForCollection(int weekIndex, String displayName, String collectionName) {
        if (serverDateObject == null) serverDateObject = new Date();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<DocumentSnapshot>> dailyTasks = new ArrayList<>();
        int startDay = (weekIndex * 7) + 1;
        int endDay = startDay + 6;
        Calendar cal = Calendar.getInstance();
        cal.setTime(serverDateObject);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        for (int d = startDay; d <= endDay; d++) {
            cal.set(Calendar.DAY_OF_MONTH, d);
            String dateStr = buildQueryDate(cal.getTime());
            Task<DocumentSnapshot> t = db.collection(collectionName)
                    .document(displayName)
                    .collection("daily")
                    .document(dateStr)
                    .get();
            dailyTasks.add(t);
        }
        return Tasks.whenAllSuccess(dailyTasks).continueWith(task -> {
            double sum = 0.0;
            List<Object> results = task.getResult();
            if (results != null) {
                for (Object obj : results) {
                    DocumentSnapshot doc = (DocumentSnapshot) obj;
                    if (doc != null && doc.exists()) {
                        Double val = parseDouble(doc.getString("total_carbon_footprint"));
                        if (val != null) sum += val;
                    }
                }
            }
            return sum;
        });
    }

    /**
     * Helper method to sum "total_carbon_footprint" for a given week for transportation.
     */
    private Task<Double> sumTransportForWeek(int weekIndex, String displayName) {
        return sumDailyDocsForCollection(weekIndex, displayName, "transportation");
    }

    /**
     * Helper method to sum "total_carbon_footprint" for a given week for food sources.
     */
    private Task<Double> sumFoodForWeek(int weekIndex, String displayName) {
        return sumDailyDocsForCollection(weekIndex, displayName, "food_sources");
    }

    /**
     * Update the PieChart gauge with the total net emission.
     */
    private void updateGauge(double totalEmission) {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry((float) totalEmission, ""));
        PieDataSet dataSet = new PieDataSet(entries, "");
        int gaugeColor;
        int emotionDrawable;
        if (totalEmission < 4) {
            gaugeColor = Color.parseColor(GREEN);
            emotionDrawable = R.drawable.happy_face;
        } else if (totalEmission < 6) {
            gaugeColor = Color.parseColor(YELLOW);
            emotionDrawable = R.drawable.poker_face;
        } else {
            gaugeColor = Color.parseColor(RED);
            emotionDrawable = R.drawable.sad_face;
        }
        dataSet.setColor(gaugeColor);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);
        dataSet.setSliceSpace(2f);
        PieData pieData = new PieData(dataSet);
        pieChart.setData(pieData);
        pieChart.invalidate();
        if (rootView != null) {
            ImageView emotionImage = rootView.findViewById(R.id.emotionImage);
            if (emotionImage != null) {
                emotionImage.setImageResource(emotionDrawable);
            }
        }
    }

    /**
     * Update the single-day bar chart.
     */
    private void updateBarChart(double transport, double food) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) transport));
        entries.add(new BarEntry(1f, (float) food));
        BarDataSet dataSet = new BarDataSet(entries, "Carbon Footprint");
        dataSet.setColors(Color.parseColor(DARK_GREEN), Color.parseColor(LIGHT_GREEN));
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (value == 0f) ? "" : String.format(Locale.getDefault(), "%.2f", value);
            }
        });
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        String[] labels = {"Transport", "Food"};
        XAxis xAxis = barChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(labels.length - 0.5f);
        barChart.getDescription().setEnabled(false);
        barChart.invalidate();
    }

    /**
     * Update the horizontal bars for Food and Transport.
     */
    private void updateHorizontalBars(double transport, double food) {
        if (!isAdded() || rootView == null) return;
        if (food == 0) {
            foodValue.setText("");
        } else {
            foodValue.setText(String.format(Locale.getDefault(), "%.2f CO2", food));
        }
        if (transport == 0) {
            transportValue.setText("");
        } else {
            transportValue.setText(String.format(Locale.getDefault(), "%.2f CO2", transport));
        }
        double maxCO2 = 100.0;
        float foodFraction = (float) Math.min(food / maxCO2, 1.0);
        float transportFraction = (float) Math.min(transport / maxCO2, 1.0);
        float totalBarWidthDp = 80f;
        float scale = rootView.getResources().getDisplayMetrics().density;
        int foodFillPx = (int) (foodFraction * totalBarWidthDp * scale);
        int transportFillPx = (int) (transportFraction * totalBarWidthDp * scale);
        ViewGroup.LayoutParams foodParams = foodBarFill.getLayoutParams();
        foodParams.width = foodFillPx;
        foodBarFill.setLayoutParams(foodParams);
        ViewGroup.LayoutParams transportParams = transportBarFill.getLayoutParams();
        transportParams.width = transportFillPx;
        transportBarFill.setLayoutParams(transportParams);
    }

    /**
     * Update the labels below the bar chart.
     */
    private void updateDayLabels() {
        if (!isAdded() || rootView == null) return;
        dayLabelsContainer.removeAllViews();
        int selectedId = radioGroupPeriod.getCheckedRadioButtonId();
        if (selectedId == R.id.radioToday) {
            dayLabelsContainer.setWeightSum(1);
            addLabel("Today");
        } else if (selectedId == R.id.radioWeek) {
            dayLabelsContainer.setWeightSum(7);
            String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            for (String day : weekDays) {
                addLabel(day);
            }
        } else if (selectedId == R.id.radioMonth) {
            dayLabelsContainer.setWeightSum(4);
            String[] weeks = {"Week1", "Week2", "Week3", "Week4"};
            for (String w : weeks) {
                addLabel(w);
            }
        }
    }

    private void addLabel(String text) {
        if (!isAdded() || rootView == null) return;
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        dayLabelsContainer.addView(tv);
    }

    /**
     * Safely parse a string into a double.
     */
    private Double parseDouble(String value) {
        if (value == null || !value.matches(".*\\d.*")) return 0.0;
        try {
            String numeric = value.replaceAll("[^\\d.]", "");
            return Double.parseDouble(numeric);
        } catch (NumberFormatException e) {
            Log.e("parseDouble", "Error parsing: " + value, e);
            return 0.0;
        }
    }
}
