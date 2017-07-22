package com.vladsaif.vkmessagestat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import com.vk.sdk.VKAccessToken;

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
        SharedPreferences sPref = getSharedPreferences(Utils.settings, MODE_PRIVATE);
        if (!sPref.contains(Utils.external_storage)) {
            SharedPreferences.Editor edit = sPref.edit();
            try {
                File test = new File(getExternalFilesDir(null), "test");
                OutputStream os = new FileOutputStream(test);
                edit.putBoolean(Utils.external_storage, true);
            } catch (IOException ex) {
                edit.putBoolean(Utils.external_storage, false);
            }
            edit.apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        dbHelper = new DbHelper(getApplicationContext(), "dialogs");
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        mRecyclerView.setAdapter(new DialogsAdapter(dbHelper.db));
        // TODO dont't forget to change title
        // TODO correct settings
        // setting common things
        SharedPreferences sPref = getSharedPreferences(Utils.settings, MODE_PRIVATE);
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
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

    }

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}

