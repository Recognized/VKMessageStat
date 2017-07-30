package com.vladsaif.vkmessagestat.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ProgressBar;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.services.MessagesCollector;

public class LoadingActivity extends AppCompatActivity {

    private Handler handler;
    private final int periodMs = 500;
    private ProgressBar progressBar;


    private ServiceConnection sConn = new ServiceConnection() {
        private MessagesCollector.Progress mBinder;
        private Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int progress = mBinder.getProgress();
                progressBar.setProgress(progress);
                if(progress < 100) helperRecur();
            }
        };
        @Override
        public void onServiceConnected(ComponentName componentName, final IBinder iBinder) {
            mBinder = (MessagesCollector.Progress) iBinder;
            helperRecur();
        }

        public void helperRecur() {
            handler.postDelayed(refresh, periodMs);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        handler = new Handler();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        bindService(new Intent(getApplicationContext(), MessagesCollector.class), sConn, 0);
    }
}
