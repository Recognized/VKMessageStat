package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;

public abstract class SetImageBase extends AsyncTask<String, Void, Bitmap> {
    protected Context context;

    protected SetImageBase(Context context) {
        this.context = context;
    }

    @Override
    protected final Bitmap doInBackground(String[] pairs) {
        String link = pairs[0];
        return CacheFile.loadBitmap(link, context);
    }

    @Override
    protected abstract void onPostExecute(Bitmap bitmap);
}
