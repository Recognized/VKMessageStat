package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.util.ArrayDeque;
import java.util.HashMap;

public abstract class SetImageBase extends AsyncTask<String, Void, Bitmap> {
    protected Context context;
    public static HashMap<String, Bitmap> cached = new HashMap<>();
    public static ArrayDeque<String> carousel = new ArrayDeque<>();
    public static int cacheSize = 5;

    protected SetImageBase(Context context) {
        this.context = context;
    }

    @Override
    protected final Bitmap doInBackground(String[] pairs) {
        String link = pairs[0];
        Bitmap cachedBitmap = cached.get(link);
        if (cachedBitmap != null) {
            return cachedBitmap;
        } else {
            Bitmap ans = CacheFile.loadBitmap(link, context);
            cached.put(link, ans);
            carousel.addLast(link);
            if (carousel.size() > cacheSize) {
                String clearCache = carousel.removeFirst();
                cached.remove(clearCache);
            }
            return ans;
        }
    }

    @Override
    protected abstract void onPostExecute(Bitmap result);
}
