package com.vladsaif.vkmessagestat.utils;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.vladsaif.vkmessagestat.utils.Easies.rus_months;

public class DateFormatter implements IAxisValueFormatter {
    private static final String LOG_TAG = DateFormatter.class.getSimpleName();
    private int start;
    private BarLineChartBase<?> chart;
    public static SimpleDateFormat day = new SimpleDateFormat("d", Locale.ENGLISH);
    public static SimpleDateFormat year = new SimpleDateFormat("yyyy", Locale.ENGLISH);
    public static SimpleDateFormat month = new SimpleDateFormat("M", Locale.ENGLISH);
    private static final int year_period = 365 * 86400;

    public DateFormatter(int start, BarLineChartBase<?> chart) {
        this.start = start;
        this.chart = chart;
    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        long unix_time = ((int) value + start) * 1000L;
        Date someDate = new Date(unix_time);
        /*Date left = new Date(((int)chart.getLowestVisibleX() + start) * 1000L);
        Date right = new Date(((int)chart.getHighestVisibleX() + start) * 1000L);*/
        return day.format(someDate) + " " +
                rus_months[Integer.decode(month.format(someDate)) - 1];

    }

    private static boolean inDifferentYears(Date a, Date b) {
        return inDifferentBase(year, a, b);
    }

    private static boolean inDifferentMonths(Date a, Date b) {
        return inDifferentBase(month, a, b);
    }

    private static boolean inDifferentBase(SimpleDateFormat format, Date a, Date b) {
        return !format.format(a).equals(format.format(b));
    }
}
