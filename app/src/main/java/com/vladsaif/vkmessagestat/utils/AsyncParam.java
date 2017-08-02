package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;

public class AsyncParam {
    public String str;
    public DialogsAdapter.ViewHolder holder;
    public int mPosition;
    public Bitmap bitmap;
    public AsyncParam(String str, DialogsAdapter.ViewHolder holder, int mPosition, Bitmap bitmap) {
        this.str = str;
        this.holder = holder;
        this.mPosition = mPosition;
        this.bitmap = bitmap;
    }
}
