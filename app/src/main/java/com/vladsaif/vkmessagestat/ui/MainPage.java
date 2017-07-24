package com.vladsaif.vkmessagestat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vladsaif.vkmessagestat.*;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.utils.SetImage;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainPage extends AppCompatActivity {

    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private DbHelper dbHelper;

    @Override
    protected void onStart() {
        super.onStart();
        // Check if app can write database to sdcard or something
        // I don't really know how to check how much memory is available
        // so there is no other way for me except forgetting about this problem
        SharedPreferences sPref = getSharedPreferences(Easies.settings, MODE_PRIVATE);
        if (!sPref.contains(Easies.external_storage)) {
            SharedPreferences.Editor edit = sPref.edit();
            try {
                File test = new File(getExternalFilesDir(null), "test");
                OutputStream os = new FileOutputStream(test);
                edit.putBoolean(Easies.external_storage, true);
            } catch (IOException ex) {
                edit.putBoolean(Easies.external_storage, false);
            }
            edit.apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        Log.d("tag", "first call Db");
        dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        DbHelper.getDialogs(dbHelper.db, getApplicationContext());
        try {
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
            Log.d("waiting", "interrupted");
        }

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(new DialogsAdapter(dbHelper, getApplicationContext(), new SetImage()));
        // TODO dont't forget to change title
        // TODO correct settings
        // setting common things
        SharedPreferences sPref = getSharedPreferences(Easies.settings, MODE_PRIVATE);
        STAT_MODE stat_mode = STAT_MODE.values()[(sPref.getInt("stat_mode", 0))];
        switch (stat_mode) {
            case SIMPLE:
                toolbar.setTitle(R.string.simple_stat);
                break;
            case ADVANCED:
                toolbar.setTitle(R.string.advanced_stat);
        }
        setSupportActionBar(toolbar);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), "access_token");

        // do stuff with RecyclerView
        Log.d("after", "toolbar");
    }

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}



