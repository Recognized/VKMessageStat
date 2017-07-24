package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.widget.ImageView;

public class AsyncParam {
    public String str;
    public ImageView imageView;
    public Context context;
    public AsyncParam(String str, ImageView imageView, Context context) {
        this.str = str;
        this.imageView = imageView;
        this.context = context;
    }
}
