package com.vladsaif.vkmessagestat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.*;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.services.MessagesCollector;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.SetImage;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainPage extends AppCompatActivity implements VKCallback<Void> {

    private final String LOG_TAG = "myTag";
    public int responses;
    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private DbHelper dbHelper;
    private Button goAdvanced;

    @Override
    protected void onStart() {
        super.onStart();
        // Check if app can write database to sdcard or something
        // I don't really know how to check how much memory is available
        // so there is no other way for me except forgetting about this problem
        responses = 0;
        SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
        if (!sPref.contains(Strings.external_storage)) {
            SharedPreferences.Editor edit = sPref.edit();
            try {
                File test = new File(getExternalFilesDir(null), "test");
                OutputStream os = new FileOutputStream(test);
                Log.d(LOG_TAG, "using external storage");
                edit.putBoolean(Strings.external_storage, true);
            } catch (IOException ex) {
                Log.d(LOG_TAG, "using internal storage");
                edit.putBoolean(Strings.external_storage, false);
            }
            edit.apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        DbHelper.getDialogs(dbHelper.db, this);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        // TODO correct settings
        // setting common things
        SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
        STAT_MODE stat_mode = STAT_MODE.values()[(sPref.getInt(Strings.stat_mode, 0))];
        switch (stat_mode) {
            case SIMPLE:
                toolbar.setTitle(R.string.simple_stat);
                break;
            case ADVANCED:
                toolbar.setTitle(R.string.advanced_stat);
        }
        setSupportActionBar(toolbar);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        goAdvanced = (Button) findViewById(R.id.button);
        goAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MessagesCollectorNew.class);
                intent.putExtra(Strings.commandType, Strings.commandDump);
                startService(intent);
                Intent openProgress = new Intent(getApplicationContext(), LoadingActivity.class);
                startActivity(openProgress);
            }
        });
    }

    @Override
    public void onResult(Void nothing) {
        Log.d(LOG_TAG, "callback response");
        responses--;
        if (responses == 0) {
            Log.d(LOG_TAG, "adapter has been set");
            mRecyclerView.setAdapter(new DialogsAdapter(dbHelper, getApplicationContext(), new SetImage()));
        }
    }

    @Override
    public void onError(VKError error) {
        // do nothing for now
        // TODO
    }

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}



