package com.vladsaif.vkmessagestat.db;

import android.util.Log;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.Serializable;
import java.util.TreeMap;

public class DialogData implements Serializable, Cloneable {
    public int dialog_id;
    public Easies.DIALOG_TYPE type;
    public String name;
    public String link;
    public int lastMessageId = -1;
    public int date = -1;
    public long messages = 0;
    public long out = 0;
    public long symbols = 0;
    public long out_symbols = 0;
    public int videos = 0;
    public int pictures = 0;
    public int audios = 0;
    public int walls = 0;
    public int gifts = 0;
    public int docs = 0;
    public int link_attachms = 0;
    public int stickers = 0;

    public int other_videos = 0;
    public int other_pictures = 0;
    public int other_audios = 0;
    public int other_walls = 0;
    public int other_gifts = 0;
    public int other_docs = 0;
    public int other_link_attachms = 0;
    public int other_stickers = 0;

    public TreeMap<Integer, Integer> chatters;

    public float[] themes_score;

    public boolean themesEnabled = false;

    public static final int GLOBAL_DATA_ID = 0;

    public DialogData(Integer dialog_id, Easies.DIALOG_TYPE type) {
        this.dialog_id = dialog_id;
        this.type = type;
        themes_score = new float[Themes.size()];
        for (int i = 0; i < themes_score.length; i++) {
            themes_score[i] = 0;
        }
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

    public void update(DialogData additional) {
        this.messages += additional.messages;
        this.out += additional.out;
        this.symbols += additional.symbols;
        this.out_symbols += additional.out_symbols;
        this.videos += additional.videos;
        this.pictures += additional.pictures;
        this.walls += additional.walls;
        this.audios += additional.audios;
        this.docs += additional.docs;
        this.link_attachms += additional.link_attachms;
        this.gifts += additional.gifts;
        this.stickers += additional.stickers;

        this.other_videos += additional.other_videos;
        this.other_pictures += additional.other_pictures;
        this.other_walls += additional.other_walls;
        this.other_audios += additional.other_audios;
        this.other_docs += additional.other_docs;
        this.other_link_attachms += additional.other_link_attachms;
        this.other_gifts += additional.other_gifts;
        this.other_stickers += additional.other_stickers;

        for (Integer i : additional.chatters.keySet()) {
            Integer k = this.chatters.get(i);
            if (k == null) this.chatters.put(i, additional.chatters.get(i));
            else this.chatters.put(i, k + additional.chatters.get(i));
        }

        for (int i = 0; i < additional.themes_score.length; i++) {
            this.themes_score[i] += additional.themes_score[i];
            Log.d("tag", "" + themes_score[i]);
        }

        if (this.messages > themesThreshold) themesEnabled = true;
    }

    private transient float[] normalized_score;
    public transient float[] relative_score;
    private transient static float[] score_min;
    private transient static float[] score_max;

    private static final int themesThreshold = 2000;

    static {
        score_min = new float[Themes.size()];
        score_max = new float[Themes.size()];
        for (int i = 0; i < Themes.size(); ++i) {
            score_min[i] = 1000; //+inf
            score_max[i] = -1;   //-inf
        }
    }

    public void normalizeScore() {
        normalized_score = new float[Themes.size()];
        float sum = 0;
        for (float f : themes_score) {
            sum += f;
        }
        if (sum == 0) sum = 1; // if it happens, other stat has no matter
        for (int i = 0; i < themes_score.length; i++) {
            normalized_score[i] = themes_score[i] / sum * 100;
            Log.d("tag", "" + themes_score[i]);
        }
        for (int i = 0; i < Themes.size(); i++) {
            score_max[i] = Math.max(score_max[i], normalized_score[i]);
            score_min[i] = Math.min(score_min[i], normalized_score[i]);
        }
    }

    public void buildRelativeScore() {
        relative_score = new float[Themes.size()];
        for (int i = 0; i < Themes.size(); ++i) {
            float diff = (score_max[i] - score_min[i]) / 2;
            float mid = score_min[i] + diff;
            relative_score[i] = (normalized_score[i] - mid) / diff * 100;
        }
    }


    public int getPositiveRelative() {
        int i = 0;
        for (float j : relative_score) {
            if (j > 0) i++;
        }
        return i;
    }

    public int getNegativeRelative() {
        int i = 0;
        for (float j : relative_score) {
            if (j <= 0) i++;
        }
        return i;
    }
}