package com.vladsaif.vkmessagestat;

import com.vladsaif.vkmessagestat.Utils;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.LinkAddress;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKApi;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.methods.VKApiUsers;

import java.io.*;
import java.util.Map;

public class MainPage extends AppCompatActivity {

    private VKAccessToken token;
    private boolean loading = true;
    int pastVisiblesItems, visibleItemCount, totalItemCount;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView.Adapter mAdapter;


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
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
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
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) // check for scroll down
                {
                    visibleItemCount = mLayoutManager.getChildCount();
                    totalItemCount = mLayoutManager.getItemCount();
                    pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

                    if (loading) {
                        if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                            loading = false;
                            Log.v("...", "Last Item Wow !");
                            // Do pagination.. i.e. fetch new data
                        }
                    }
                }
            }
        });
        LayoutInflater inflater = getLayoutInflater();
        LinearLayout dialog = (LinearLayout) inflater.inflate(R.layout.dialog, dialogs, false);
        fillDialogView(dialog, null, "Name", 1, 1);
        dialogs.addView(dialog);


    }

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}

