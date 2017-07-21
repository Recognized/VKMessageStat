package com.vladsaif.vkmessagestat;

import android.graphics.Bitmap;

public class DialogData {
    // TODO here I should fetch my dialogs info from database
    private Bitmap avatar;
    private String name;
    private Integer messages;
    private Integer symbols;

    public DialogData

    public Integer getSymbols() {
        return symbols;
    }

    public Integer getMessages() {
        return messages;
    }

    public String getName() {
        return name;
    }

    public Bitmap getAvatar() {
        return avatar;
    }
}