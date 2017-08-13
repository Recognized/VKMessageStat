package com.vladsaif.vkmessagestat.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ProgressBar;
import com.vk.sdk.VKAccessToken;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.utils.Strings;

public class MainPage extends AppCompatActivity {
    private static final String LOG_TAG = MainPage.class.getSimpleName();
    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        toolbar.setTitle("Статистика сообщений");
        setSupportActionBar(toolbar);
        mProgress = (ProgressBar) findViewById(R.id.loadingRecycler);
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        new AsyncTask<Void, Void, DialogsAdapter>() {

            @Override
            protected DialogsAdapter doInBackground(Void... objects) {
                return new DialogsAdapter(getApplicationContext());
            }

            @Override
            protected void onPostExecute(DialogsAdapter dialogsAdapter) {
                mProgress.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mRecyclerView.setAdapter(dialogsAdapter);
            }
        }.execute();
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {

        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }
}



