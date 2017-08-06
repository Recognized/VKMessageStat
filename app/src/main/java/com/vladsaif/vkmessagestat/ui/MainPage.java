package com.vladsaif.vkmessagestat.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.services.Dumper;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.services.VKWorker;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainPage extends AppCompatActivity {

    private final String LOG_TAG = "myTag";
    public int responses;
    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private ProgressBar mProgress;
    private DbHelper dbHelper;
    private Button goAdvanced;
    public VKWorker vkWorker;
    public Dumper dumper;
    public Handler requestHandler;
    public Handler dataHandler;
    private String access_token;
    private SQLiteDatabase db;
    public int dialogsCount;


    @Override
    protected void onStart() {
        super.onStart();
        // Check if app can write database to sdcard or something
        // I don't really know how to check how much memory is available
        // so there is no other way for me except forgetting about this problem
        responses = 0;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        mProgress = (ProgressBar) findViewById(R.id.loadingRecycler);
        dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
        db = dbHelper.getWritableDatabase();
        dbHelper.onCreate(db);
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        access_token = VKAccessToken.currentToken().accessToken;
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        (new PrepareThreads()).execute();
    }

    public final class PrepareThreads extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            dumper = new Dumper();
            dumper.start();
            while (dumper.dumper == null) ;
            vkWorker = new VKWorker(dumper.dumper, access_token);
            vkWorker.start();
            while (vkWorker.mHandler == null) ;
            requestHandler = vkWorker.mHandler;
            dataHandler = dumper.dumper;
            dumper.setOnFinishGetDialogs(onFinishGetDialogs);
            Log.d(LOG_TAG, "Threads are prepared");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Message m = requestHandler.obtainMessage();
            m.what = VKWorker.GET_DIALOGS;
            m.arg1 = 20;
            m.arg2 = 0;
            requestHandler.sendMessage(m);
            Log.d(LOG_TAG, "Requests sent");
        }
    }

    private Runnable onFinishGetDialogs = new Runnable() {
        @Override
        public void run() {
            mProgress.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAdapter(new DialogsAdapter(dbHelper, MainPage.this, mLayoutManager));
            mRecyclerView.setOnScrollListener(((DialogsAdapter)mRecyclerView.getAdapter()).scrolling);
        }
    };

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}



