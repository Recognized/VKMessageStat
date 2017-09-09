package com.vladsaif.vkmessagestat.charts;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class MessageDataSetBase extends LineDataSet {
    private static final String LOG_TAG = MessageDataSetBase.class.getSimpleName();
    protected List<Entry> entries = new ArrayList<>();
    protected Context context;

    public MessageDataSetBase(String label, int dialog_id, Context context) {
        super(null, label);
        this.context = context;
        int[] rawEntries = dialog_id == DialogData.GLOBAL_DATA_ID ? deserializeGlobalEntries(context) :
                deserializeRawEntries(dialog_id, context);
        if (rawEntries == null) {
            Log.d(LOG_TAG, "Getting raw values");
            rawEntries = readRawEntries(dialog_id, context);
            Arrays.sort(rawEntries);
            serializeRawEntries(rawEntries, dialog_id, context);
        }
        proceed(rawEntries);
        for (Entry e : entries) {
            this.addEntry(e);
        }
        this.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        this.setDrawCircles(false);
        this.setLineWidth(2f);
        this.setDrawValues(false);
        this.setValueFormatter(new IValueFormatter() {
            @Override
            public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                return "";
            }
        });
        // TODO other parameters;
    }

    protected abstract void proceed(int[] rawEntries);

    public static int[] deserializeRawEntries(int dialog_id, Context context) {
        String fileName = "entries" + Integer.toString(dialog_id);
        File entriesFile = new File(Easies.getSerializablePath(context) + fileName);
        Log.d(LOG_TAG, "FILENAME: " + entriesFile.getAbsolutePath());
        if (entriesFile.exists()) {
            try {
                ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(entriesFile));
                int[] prevEntries = (int[]) inputStream.readObject();
                inputStream.close();
                return prevEntries;
            } catch (IOException ex) {
                Log.wtf(LOG_TAG, ex);
            } catch (ClassNotFoundException cnfe) {
                Log.wtf(LOG_TAG, cnfe);
            }
        }
        return null;
    }

    public static int[] deserializeGlobalEntries(Context context) {
        File dataPath = new File(Easies.getSerializablePath(context));
        int[] array = new int[(int) DialogsAdapter.getData(context).get(DialogData.GLOBAL_DATA_ID).messages];
        int pos = 0;
        File[] files = dataPath.listFiles();
        outer:
        for (File file : files) {
            if (file.getName().startsWith("entries")) {
                try {
                    ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
                    int[] prevEntries = (int[]) inputStream.readObject();
                    inputStream.close();
                    for (int prevEntry : prevEntries) {
                        if (pos > array.length - 1) break outer;
                        array[pos++] = prevEntry;
                    }
                } catch (IOException ex) {
                    Log.wtf(LOG_TAG, ex);
                } catch (ClassNotFoundException cnfe) {
                    Log.wtf(LOG_TAG, cnfe);
                }
            }
        }
        Arrays.sort(array);
        Log.d(LOG_TAG, Arrays.deepToString(files));
        return array;
    }

    public static void serializeRawEntries(int[] rawEntries, int dialog_id, Context context) {
        String fileName = idToFilename(dialog_id);
        if (dialog_id == DialogData.GLOBAL_DATA_ID) Arrays.sort(rawEntries);
        Easies.serializeObject(rawEntries, fileName, context);
    }

    private static String idToFilename(int dialog_id) {
        return "entries" + Integer.toString(dialog_id);
    }

    private static int[] readRawEntries(int dialog_id, Context context) {
        int[] array = new int[(int) DialogsAdapter.getData(context).get(dialog_id).messages];
        int pos = 0;
        String filename = Easies.getTextDataPath(context) + Integer.toString(dialog_id) + ".txt";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
            String str;
            while ((str = br.readLine()) != null) {
                int token_number = 0;
                for (int i = 0; i < str.length(); ++i) {
                    if (str.codePointAt(i) == '[') token_number++;
                    if (token_number == 5) {
                        array[pos++] = Integer.parseInt(str.substring(i + 1, str.length() - 1));
                        break;
                    }
                }
            }
            br.close();
            return array;
        } catch (IOException ex) {
            Log.wtf(LOG_TAG, ex);
            return null;
        }
    }
}
