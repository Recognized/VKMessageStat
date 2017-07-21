package com.vladsaif.vkmessagestat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

public class DbHelper {
    private SQLiteDatabase db;
    private SQLiteDatabase readDb;
    private SQLiteDatabase writeDb;
    private File dbFile;
    private String dbName;
    private boolean isExistDB;
    public DbHelper(Context context, String dbName) {
        this.isExistDB = false;
        this.dbName = dbName;
        this.prepareBD();
        if(!isExistDB){
            onCreate(db);
        }
        this.setReadDb();
        this.setWriteDb();
    }

    private void prepareBD() {
        try{
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath () + "/" + dbName);
            directory.mkdirs();
            dbFile = new File(directory, dbName);
            if(dbFile.exists())isExistDB=true;
            this.db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        }catch (Exception e) {
            this.db = SQLiteDatabase.openOrCreateDatabase(dbName, null);
        }
    }

    public void onCreate(SQLiteDatabase db) {
        // TODO don't forger
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO this too
    }

    public void close(){
        db.close();
        writeDb.close();
        readDb.close();
    }

    public SQLiteDatabase openMyDb() {
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null);
    }

    public SQLiteDatabase getReadableDatabase(){
        return this.readDb;
    }

    private void setReadDb(){
        this.readDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    public SQLiteDatabase getWritableDatabase(){
        return this.writeDb;
    }

    private void setWriteDb(){
        this.writeDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }
}