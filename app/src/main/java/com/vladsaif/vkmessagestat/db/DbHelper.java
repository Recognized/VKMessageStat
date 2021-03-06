package com.vladsaif.vkmessagestat.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.vladsaif.vkmessagestat.utils.Strings;


public class DbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String LOG_TAG = "DbHelper";
    public SQLiteDatabase db;

    public DbHelper(final Context context, String databaseName) {
        super(new DatabaseContext(context), databaseName, null, DATABASE_VERSION);
        db = getWritableDatabase();
        onCreate(db);
    }

    @Override
    public void onCreate(SQLiteDatabase data) {
        Log.d(LOG_TAG, "oncreate");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.dialogs +        " (dialog_id INTEGER PRIMARY KEY, type TEXT, date INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.last_message_id +" (dialog_id INTEGER PRIMARY KEY, message_id INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.names +          " (dialog_id INTEGER PRIMARY KEY, name TEXT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.pictures +       " (dialog_id INTEGER PRIMARY KEY, link TEXT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.advanced +       " (dialog_id INTEGER PRIMARY KEY, symbols INT, out INT," +
                "                                                        photos INT, videos INT, walls INT, audios INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.counts + " (dialog_id INTEGER PRIMARY KEY, counter INT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        //pass TODO maybe, or don't give a fuck about this
    }
}