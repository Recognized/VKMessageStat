package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import com.vladsaif.vkmessagestat.charts.MessageDataSetBase;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class DataManager {
    public static final String LOG_TAG = DataManager.class.getSimpleName();
    private SparseArray<BufferedWriter> writers = new SparseArray<>();
    private String dataPath;
    private String serializablePath;

    public DataManager(Context context) {
        this.dataPath = Easies.getTextDataPath(context);
        this.serializablePath = Easies.getSerializablePath(context);
        if ((new File(dataPath)).mkdir()) {
            Log.d(LOG_TAG, "TextData path was created");
        }
    }

    public BufferedWriter getWriter(int dialog_id) {
        BufferedWriter fileWriter = writers.get(dialog_id, null);
        if (fileWriter != null) {
            return fileWriter;
        } else {
            String filePath = dataPath + Integer.toString(dialog_id) + ".txt";
            try {
                fileWriter = new BufferedWriter
                        (new OutputStreamWriter(new FileOutputStream(filePath), Charset.forName("UTF-8")));
                writers.put(dialog_id, fileWriter);
            } catch (IOException noDirectory) {
                Log.wtf(LOG_TAG, "Directory must has been created in constructor");
            }
            return fileWriter;
        }
    }

    public ArrayList<Integer> getPrevEntries(int dialog_id, Context context) {
        int[] prevEntries = MessageDataSetBase.deserializeRawEntries(dialog_id, context);
        if (prevEntries != null) {
            ArrayList<Integer> prev = new ArrayList<>(prevEntries.length);
            for (int i : prevEntries) {
                prev.add(i);
            }
            return prev;
        } else {
            return new ArrayList<>();
        }
    }

    public void saveEntries(ArrayList<Integer> src, int dialog_id, Context context) {
        int[] array = new int[src.size()];
        for (int i = 0; i < src.size(); ++i) {
            array[i] = src.get(i);
        }
        MessageDataSetBase.serializeRawEntries(array, dialog_id, context);
    }

    public void close(int dialog_id) {
        BufferedWriter pw = writers.get(dialog_id);
        writers.remove(dialog_id);
        try {
            pw.close();
        } catch (IOException ex) {
            Log.wtf(LOG_TAG, ex.toString());
        }
    }
}
