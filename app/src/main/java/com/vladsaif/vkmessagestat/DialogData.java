package com.vladsaif.vkmessagestat;

import android.graphics.Bitmap;

public class DialogData {
    // TODO here I should fetch my dialogs info from database
    public String link;
    public String name;
    public Integer messages;
    public Integer symbols;
    public Integer dialog_id;
    public Utils.DIALOG_TYPE type;

    public DialogData(Integer dialog_id, Utils.DIALOG_TYPE type) {
        this.dialog_id = dialog_id;
        this.type = type;
    }

    public DialogData(Integer dialog_id, String name, String link, Integer mcounter, Integer scounter) {
        this.dialog_id = dialog_id;
        this.name = name;
        this.link = link;
        this.messages = mcounter;
        this.symbols = scounter;
    }
}