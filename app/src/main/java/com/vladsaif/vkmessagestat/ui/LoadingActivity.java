package com.vladsaif.vkmessagestat.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.Easies;

public class LoadingActivity extends AppCompatActivity {

    private Handler handler;
    private final int periodMs = 500;
    private ProgressBar progressBar;
    private FrameLayout messageContainter;


    private ServiceConnection sConn = new ServiceConnection() {
        private MessagesCollectorNew.Progress mBinder;
        private Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int progress = mBinder.getProgress();
                progressBar.setProgress(progress);
                MessageData data = mBinder.getSomeMessage();
                messageContainter.removeAllViews();
                View message = LayoutInflater.from(messageContainter.getContext())
                        .inflate(R.layout.message, messageContainter, true);
                fillData(data, message);
                if(progress < 100) helperRecur();
            }
        };
        @Override
        public void onServiceConnected(ComponentName componentName, final IBinder iBinder) {
            mBinder = (MessagesCollectorNew.Progress) iBinder;
            helperRecur();
        }

        public void helperRecur() {
            handler.postDelayed(refresh, periodMs);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private void fillData(MessageData data, View v) {
        TextView content = v.findViewById(R.id.content);
        content.setText(data.message);
        TextView time = v.findViewById(R.id.dialog_title);
        time.setText(data.name);
        TextView date = v.findViewById(R.id.time);
        date.setText(Easies.dateToHumanReadable(data.date));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        handler = new Handler();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageContainter  = (FrameLayout) findViewById(R.id.message_container);
        bindService(new Intent(getApplicationContext(), MessagesCollectorNew.class), sConn, 0);
    }
}
