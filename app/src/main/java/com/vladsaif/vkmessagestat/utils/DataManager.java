package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class DataManager {
    public static final String LOG_TAG = DataManager.class.getSimpleName();
    private SparseArray<BufferedWriter> writers;
    private String dataPath;

    public DataManager(Context context) {
        writers = new SparseArray<>();
        this.dataPath = Easies.getTextDataPath(context);
        if ((new File(dataPath)).mkdir()) {
            Log.d(LOG_TAG, "TextData path was created");
        }
        ;
    }

    public BufferedWriter getWriter(int dialog_id) {
        BufferedWriter fileWriter = writers.get(dialog_id, null);
        if (fileWriter != null) {
            return fileWriter;
        } else {
            String filePath = dataPath + Integer.toString(dialog_id) + ".txt";
            try {
                fileWriter = new BufferedWriter
                        (new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8));
                writers.put(dialog_id, fileWriter);
            } catch (IOException noDirectory) {
                Log.wtf(LOG_TAG, "Directory must has been created in constructor");
            }
            return fileWriter;
        }
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
