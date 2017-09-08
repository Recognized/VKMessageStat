package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

public class SetImageSimple extends SetImageBase {

    public SetImageSimple(ImageView view, Context context) {
        super(view, context);
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (result != null) Easies.imageViewAnimatedChange(view, result, context);
    }
}
