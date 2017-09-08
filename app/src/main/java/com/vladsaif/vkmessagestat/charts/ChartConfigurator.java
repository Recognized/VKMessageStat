package com.vladsaif.vkmessagestat.charts;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.util.Log;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.db.GlobalData;
import com.vladsaif.vkmessagestat.utils.DateFormatter;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


public class ChartConfigurator {

    private ChartConfigurator() {
    }

    public static void makeSimpleLineChart(LineChart chart, LineDataSet dataSet, int firstMessageTime, int lastMessageTime, Context context) {
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setDragEnabled(true);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getAxisRight().setEnabled(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        leftAxis.setAxisMinimum(0);
        // leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawZeroLine(true);

        XAxis xAxis = chart.getXAxis();
        long start = firstMessageTime * 1000L;
        SimpleDateFormat df = new SimpleDateFormat("MMM d HH:mm:ss zzz yyyy", Locale.ENGLISH);
        while (true) {
            Date date = new Date(start + 365 * 86400 * 1000L);
            Date a = null;
            try {
                a = df.parse("Jan 1 00:00:00 EDT " + DateFormatter.year.format(date));
            } catch (ParseException pe) {
                Log.wtf("YOU LOSE", pe);
            }
            LimitLine limitLine = new LimitLine(a.getTime() / 1000 - firstMessageTime, DateFormatter.year.format(date));
            limitLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
            limitLine.enableDashedLine(10f, 10f, 0);
            limitLine.setLineColor(ContextCompat.getColor(context, R.color.light_blue));
            xAxis.addLimitLine(limitLine);
            if (start > lastMessageTime * 1000L) break;
            start += 365 * 86400 * 1000L;

        }
        xAxis.setDrawLimitLinesBehindData(true);
        xAxis.setAxisMinimum(0);
        xAxis.setAxisMaximum(lastMessageTime - firstMessageTime);
        // xAxis.enableGridDashedLine(10f, 10f, 0f);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(86400 * 3);
        xAxis.setLabelRotationAngle(30);
        xAxis.setLabelCount(4);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        xAxis.setValueFormatter(new DateFormatter(firstMessageTime, chart));
        Paint paintAxisLabels = chart.getRendererXAxis().getPaintAxisLabels();
        paintAxisLabels.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        paintAxisLabels.setTypeface(Easies.getFont(context, "mono.ttf"));
        LineData lineData = new LineData(dataSet);
        lineData.setDrawValues(true);
        lineData.setHighlightEnabled(false);
        chart.setContentDescription("");
        chart.setData(lineData);
        chart.setOnChartValueSelectedListener(new MyListener(chart));
        Description description = new Description();
        description.setText("");
        chart.setDescription(description);
        chart.invalidate();

    }

    public static void makeMessageBarChart(BarChart chart, BarDataSet barDataSet, int[] myColors, Context context) {
        barDataSet.setStackLabels(new String[]{"исходящие", "входящие"});
        barDataSet.setColors(myColors);
        barDataSet.setDrawValues(false);
        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(300f);

        chart.setData(barData);
        chart.setFitBars(true);
        chart.getRenderer().getPaintValues().setStyle(Paint.Style.STROKE);
        chart.getXAxis().setEnabled(false);
        chart.getAxisLeft().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(Color.BLACK);
        chart.getLegend().setTextSize(13);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.invalidate();
        chart.setBorderWidth(0);
        chart.setMinimumWidth(1000);
    }

    public static void makePieChartChatters(PieChart chart, DialogData data, GlobalData globalData, int[] colors, Context context) {
        List<PieEntry> entries = new ArrayList<>();
        ArrayList<Pair<Integer, Integer>> src = new ArrayList<>();
        for (Integer i : data.chatters.keySet()) {
            src.add(new Pair<>(i, data.chatters.get(i)));
        }
        Collections.sort(src, new Comparator<Pair<Integer, Integer>>() {
            @Override
            public int compare(Pair<Integer, Integer> integerIntegerPair, Pair<Integer, Integer> t1) {
                return t1.second - integerIntegerPair.second;
            }
        });
        long sum = 0;
        for (Pair<Integer, Integer> pair : src) {
            sum += pair.second;
        }
        float percentage = 0;
        for (int i = 0; i < src.size() && i < 10; i++) {
            float value = ((float) src.get(i).second) / sum * 100;
            if (value < 5) {
                break;
            }
            entries.add(new PieEntry(value, cutSurname(globalData.getUser(src.get(i).first).name)));
            percentage += value;
        }
        entries.add(new PieEntry(100 - percentage, "Остальные"));
        PieDataSet dataSet = new PieDataSet(entries, "Наиболее активные");
        dataSet.setValueTextSize(12);
        dataSet.setSliceSpace(3);
        dataSet.setColors(colors);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.3f);
        dataSet.setValueLinePart2Length(0.2f);
        dataSet.setValueLineVariableLength(true);
        dataSet.setValueFormatter(new PercentFormatter());
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        PieData pieData = new PieData(dataSet);
        pieData.setValueTextColor(Color.BLACK);
        chart.setData(pieData);
        chart.setDrawEntryLabels(true);
        chart.getLegend().setEnabled(false);
        chart.setEntryLabelColor(Color.BLACK);
    }

    public static class MyListener implements OnChartValueSelectedListener {
        private BarLineChartBase mChart;


        public MyListener(BarLineChartBase mChart) {
            this.mChart = mChart;
        }

        @Override
        public void onValueSelected(Entry e, Highlight h) {
            mChart.centerViewToAnimated(e.getX(), e.getY(), mChart.getData().getDataSetByIndex(h.getDataSetIndex())
                    .getAxisDependency(), 500);
        }

        @Override
        public void onNothingSelected() {
        }
    }

    private static String cutSurname(String name) {
        String[] s = name.split("\\W+");
        return s[0] + " " + s[1].charAt(0) + ".";
    }
}
