package com.vladsaif.vkmessagestat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteAbortException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.AnyRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import com.vk.sdk.VKAccessToken;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

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
        Log.d("after", "toolbar");
    }

    static class SetImage extends AsyncTask<AsyncParam, Void, Pair<Bitmap, ImageView> > {
        @Override
        protected Pair<Bitmap, ImageView> doInBackground(AsyncParam[] params) {
            String link = params[0].str;
            Bitmap bitmap = null;
            if (!link.equals("no_photo")) {
                try {
                    InputStream inputStream = new URL(link).openStream();   // Download Image from URL
                    bitmap = Utils.getCircleBitmap(BitmapFactory.decodeStream(inputStream));       // Decode Bitmap
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Utils.savePic(bitmap, Utils.transformLink(link), params[0].context.getApplicationContext() );
            } else {
                bitmap = BitmapFactory.decodeResource(params[0].context.getResources(), R.drawable.stub);
            }
            return new Pair<>(bitmap, params[0].imageView);
        }
        protected void onProgressUpdate(Void... params) {
        }

        protected void onPostExecute(Pair<Bitmap, ImageView> result) {
            result.second.setImageBitmap(result.first);
        }
    }

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}

class AsyncParam {
    public String str;
    public ImageView imageView;
    public Context context;
    public AsyncParam(String str, ImageView imageView, Context context) {
        this.str = str;
        this.imageView = imageView;
        this.context = context;
    }
}

