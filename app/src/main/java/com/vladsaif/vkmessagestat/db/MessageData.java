package com.vladsaif.vkmessagestat.db;

/**
 * Created by el-gl on 30.07.2017.
 */
public class MessageData {
    public String message;
    public int date;
    public int dialog_id;

    public MessageData(int dialog_id, String message, int date) {
        this.date = date;
        this.dialog_id = dialog_id;
        this.message = message;
    }


}