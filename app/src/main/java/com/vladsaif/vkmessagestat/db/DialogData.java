package com.vladsaif.vkmessagestat.db;

import com.vladsaif.vkmessagestat.utils.Easies;

public class DialogData {
    // TODO here I should fetch my dialogs info from database
    public String link;
    public String name;
    public Integer messages;
    public Integer symbols;
    public Integer dialog_id;
    public Easies.DIALOG_TYPE type;

    public DialogData(Integer dialog_id, Easies.DIALOG_TYPE type) {
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