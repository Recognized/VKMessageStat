package com.vladsaif.vkmessagestat;

import android.graphics.Bitmap;

public class DialogData {
    // TODO here I should fetch my dialogs info from database
    private Bitmap avatar;
    private String name;
    private Integer messages;
    private Integer symbols;
    private Integer dialog_id;

    public DialogData(Integer dialog_id, String name, Bitmap avatar, Integer mcounter, Integer scounter) {
        this.dialog_id = dialog_id;
        this.name = name;
        this.avatar = avatar;
        this.messages = mcounter;
        this.symbols = scounter;
    }

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

    public Integer getDialog_id() {
        return dialog_id;
    }
}