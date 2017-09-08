package com.vladsaif.vkmessagestat.db;

import android.content.Context;
import android.util.Log;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class Themes {
    private static final String LOG_TAG = Themes.class.getSimpleName();
    public static boolean initialized = false;
    private static String theme_folder = "themes";
    private static ArrayList<HashMap<String, Float>> themesArray = new ArrayList<>();
    private static ArrayList<String> order = new ArrayList<>();

    public static void initialize(Context context) {
        if (initialized) return;
        try {
            makeOrder(context);
            int word_counter = 0;
            for (String filename : order) {
                HashMap<String, Float> current_theme = new HashMap<>();
                InputStream is = context.getAssets().open(theme_folder + "/" + filename);
                Easies.FastScanner scanner = new Easies.FastScanner(is);
                while (true) {
                    String word = scanner.next();
                    if (word == null) break;
                    float value = (float) scanner.nextDouble();
                    current_theme.put(word, value);
                    word_counter++;
                }
                themesArray.add(current_theme);
            }
            Log.d(LOG_TAG, "" + word_counter);
            initialized = true;
        } catch (IOException io) {
            Log.wtf(LOG_TAG, io);
        }
    }

    public static void makeOrder(Context context) {
        try {
            String[] filenames = context.getResources().getAssets().list(theme_folder);
            order.addAll(Arrays.asList(filenames));
            Collections.sort(order);
        } catch (IOException io) {
            Log.wtf(LOG_TAG, io);
        }
    }

    public static void updateScore(DialogData data, String Words) {
        String[] words = Words.toLowerCase().trim().split("\\W+");
        for (String word : words) {
            for (int i = 0; i < themesArray.size(); i++) {
                Float res = themesArray.get(i).get(word);
                if (res != null) {
                    data.themes_score[i] += Math.pow(res, 3);
                }
            }
        }
    }

    public static String getThemeName(int pos) {
        return order.get(pos).substring(0, order.get(pos).length() - 4);
    }

    public static int size() {
        return order.size();
    }
}
