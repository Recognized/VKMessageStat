package com.vladsaif.vkmessagestat.db;

public class MessageData {
    public String message;
    public int date;
    public int dialog_id;
    public DialogData data;
    public String name;
    public int id;

    public MessageData(int dialog_id, String message, int date, DialogData data, int id,
                       String name) {
        this.name = name;
        this.date = date;
        this.dialog_id = dialog_id;
        this.message = message;
        this.data = data;
        this.id = id;
    }


}
