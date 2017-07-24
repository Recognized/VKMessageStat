package com.vladsaif.vkmessagestat.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Pair;
import android.widget.ImageView;
import com.vladsaif.vkmessagestat.R;

import java.io.InputStream;
import java.net.URL;

public class SetImage extends AsyncTask<AsyncParam, Void, Pair<Bitmap, ImageView> > {
    @Override
    protected Pair<Bitmap, ImageView> doInBackground(AsyncParam[] params) {
        String link = params[0].str;
        Bitmap bitmap = null;
        if (!link.equals("no_photo")) {
            try {
                InputStream inputStream = new URL(link).openStream();   // Download Image from URL
                bitmap = Easies.getCircleBitmap(BitmapFactory.decodeStream(inputStream));       // Decode Bitmap
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Easies.savePic(bitmap, Easies.transformLink(link), params[0].context.getApplicationContext() );
        } else {
            bitmap = BitmapFactory.decodeResource(params[0].context.getResources(), R.drawable.stub);
        }
        return new Pair<>(bitmap, params[0].imageView);
    }
    protected void onProgressUpdate(Void... params) {
    }

    protected void onPostExecute(Pair<Bitmap, ImageView> result) {
        result.second.setImageBitmap(result.first);
    }
}