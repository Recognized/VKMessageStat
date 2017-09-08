package com.vladsaif.vkmessagestat.charts;

import android.content.Context;
import android.util.Log;
import com.github.mikephil.charting.data.Entry;

public class MessageDataSetAccum extends MessageDataSetBase {
    private final static String LOG_TAG = MessageDataSetAccum.class.getSimpleName();
    public int firstMessageTime;
    public int lastMessage;

    public MessageDataSetAccum(String label, int dialog_id, Context context) {
        super(label, dialog_id, context);
    }

    @Override
    protected void proceed(int[] rawEntries) {
        Log.d(LOG_TAG, "Proceed begins");
        firstMessageTime = rawEntries[0];
        int delta = 86400;
        int time_block = rawEntries[0];
        int max = 0;
        int temp = 0;
        boolean sorted = true;
        for (int i = 0; i < rawEntries.length - 1; i++) {
            if (rawEntries[i + 1] < rawEntries[i]) sorted = false;
        }
        assert sorted;
        for (int rawEntry : rawEntries) {
            max = Math.max(rawEntry, max);
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
        Log.d(LOG_TAG, Integer.toString(max));
        Log.d(LOG_TAG, "Proceed ended");
    }
}
