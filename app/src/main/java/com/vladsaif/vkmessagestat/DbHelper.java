package com.vladsaif.vkmessagestat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    public SQLiteDatabase db;

    public DbHelper(final Context context, String databaseName)  {
        super(new DatabaseContext(context), databaseName, null, DATABASE_VERSION);
        db = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase data) {
        data.execSQL("CREATE TABLE IF NOT EXISTS dialogs (dialog_id INTEGER PRIMARY KEY, name TEXT, avatar_path TEXT, " +
                "mcounter INT, scounter INT)");
        data.execSQL("CREATE TABLE IF NOT EXISTS last_message_id (dialog_id INTEGER PRIMARY KEY, message_id INT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        //pass TODO maybe, or don't give a fuck about this
    }
}