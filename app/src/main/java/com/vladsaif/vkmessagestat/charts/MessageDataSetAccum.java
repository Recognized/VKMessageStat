package com.vladsaif.vkmessagestat.charts;

import android.content.Context;
import android.util.Log;
import com.github.mikephil.charting.data.Entry;

public class MessageDataSetAccum extends MessageDataSetBase {
    private final static String LOG_TAG = MessageDataSetAccum.class.getSimpleName();
    public int firstMessageTime;
    public int lastMessage;
    public int dialog_id;

    public MessageDataSetAccum(String label, int dialog_id, Context context) {
        super(label, dialog_id, context);
        this.dialog_id = dialog_id;
    }

    @Override
    protected void proceed(int[] rawEntries) {
        Log.d(LOG_TAG, "Proceed begins");
        firstMessageTime = rawEntries[0];
        int delta = 86400;
        int time_block = rawEntries[0];
        int max = 0;
        int temp = 0;
        int min = Integer.MAX_VALUE;
        Log.d(LOG_TAG, rawEntries.length + "");
        for (int rawEntry : rawEntries) {
            max = Math.max(rawEntry, max);
            min = Math.min(rawEntry, min);
            if (rawEntry < time_block + delta) {
                temp++;
            } else {
                entries.add(new Entry(time_block - firstMessageTime, temp));
                time_block += delta;
                while (rawEntry > time_block) {
                    entries.add(new Entry(time_block - firstMessageTime, temp));
                    time_block += delta;
                }
            }
        }
        lastMessage = max;
        Log.d(LOG_TAG, Integer.toString(max) + "max");
        Log.d(LOG_TAG, Integer.toString(min) + "min");
        Log.d(LOG_TAG, "Proceed ended");
    }
}
