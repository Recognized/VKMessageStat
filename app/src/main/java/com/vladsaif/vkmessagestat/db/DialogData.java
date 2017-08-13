package com.vladsaif.vkmessagestat.db;

import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class DialogData implements Serializable, Cloneable {
    public int dialog_id;
    public Easies.DIALOG_TYPE type;
    public String name;
    public String link;
    public int lastMessageId = -1;
    public int date = -1;
    public long messages = 0;
    public long symbols = 0;
    public long videos = 0;
    public long pictures = 0;
    public long audios = 0;
    public long walls = 0;
    public long out = 0;
    public long out_symbols = 0;
    public long gifts = 0;
    public long docs = 0;
    public long link_attachms = 0;
    public long stickers = 0;
    public int mposition;
    public int sposition;
    public TreeMap<Integer, Integer> chatters;

    public DialogData(Integer dialog_id, Easies.DIALOG_TYPE type) {
        this.dialog_id = dialog_id;
        this.type = type;
        chatters = new TreeMap<>();
    }

    @Override
    public DialogData clone() {
        try {
            return (DialogData) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InternalError();
        }
    }

    public void update(long messages, long symbols, long videos, long pictures,
                       long audios, long walls, long out, long out_symbols,
                       long docs, long links, long gifts, long stickers, Map<Integer, Integer> chatters) {
        this.messages += messages;
        this.symbols += symbols;
        this.videos += videos;
        this.pictures += pictures;
        this.walls += walls;
        this.audios += audios;
        this.out += out;
        this.out_symbols += out_symbols;
        this.docs += docs;
        this.link_attachms += links;
        this.gifts += gifts;
        this.stickers += stickers;
        for (Integer i : chatters.keySet()) {
            Integer k = this.chatters.get(i);
            if (k == null) this.chatters.put(i, chatters.get(i));
            else this.chatters.put(i, k + chatters.get(i));
        }

    }
}