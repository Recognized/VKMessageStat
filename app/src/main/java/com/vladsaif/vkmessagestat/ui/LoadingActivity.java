package com.vladsaif.vkmessagestat.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.CacheFile;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.SetImageBase;

public class LoadingActivity extends AppCompatActivity {
    public static final String LOG_TAG = LoadingActivity.class.getSimpleName();
    private Handler handler;
    private final int periodProgress = 500;
    private final int periodMessage = 3000;
    private ProgressBar progressBar;
    private LinearLayout messageContainer;
    private int currentCounter;
    private int prevId = -1;

    private ServiceConnection sConn = new ServiceConnection() {
        private MessagesCollectorNew.Progress mBinder;
        private Runnable refreshProgress = new Runnable() {
            @Override
            public void run() {
                int progress = mBinder.getProgress();
                progressBar.setProgress(progress);
                if (progress < 100) handler.postDelayed(refreshProgress, periodProgress);
            }
        };
        private Runnable refreshMessage = new Runnable() {
            @Override
            public void run() {
                MessageData data = mBinder.getSomeMessage();
                if(data != null && data.id != prevId) {
                    prevId = data.id;
                    Log.d(LOG_TAG, "Data isn't null");
                    messageContainer.removeAllViewsInLayout();
                    View message = LayoutInflater.from(messageContainer.getContext())
                            .inflate(R.layout.message, messageContainer, true);
                    fillData(data, message);
                }
                handler.postDelayed(refreshMessage, periodMessage);
            }
        };
        @Override
        public void onServiceConnected(ComponentName componentName, final IBinder iBinder) {
            mBinder = (MessagesCollectorNew.Progress) iBinder;
            handler.post(refreshMessage);
            handler.post(refreshProgress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            handler.removeCallbacksAndMessages(null);
        }
    };

    private void fillData(MessageData messageData, View v) {
        TextView content = v.findViewById(R.id.content);
        content.setText(messageData.message);
        TextView name = v.findViewById(R.id.dialog_title);
        name.setText(messageData.data.name);
        TextView date = v.findViewById(R.id.time);
        date.setText(Easies.dateToHumanReadable(messageData.date));
        ImageView avatar = v.findViewById(R.id.main_page_avatar);
        CacheFile.setDefaultImage(avatar, messageData.data.type, this);
        (new SetImage(avatar, ++currentCounter, this)).execute(messageData.data.link);
    }

    class SetImage extends SetImageBase {
        private ImageView view;
        private int counter;

        public SetImage(ImageView view, int counter, Context context) {
            super(context);
            this.view = view;
            this.counter = counter;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null && currentCounter == counter) {
                Log.d(LOG_TAG, "image has been set");
                Easies.imageViewAnimatedChange(view, bitmap, context);
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        currentCounter = 0;
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.download_title));
        setSupportActionBar(toolbar);
        handler = new Handler();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageContainer = (LinearLayout) findViewById(R.id.message_container);
        bindService(new Intent(getApplicationContext(), MessagesCollectorNew.class), sConn, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
