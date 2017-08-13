package com.vladsaif.vkmessagestat.ui;

import android.animation.LayoutTransition;
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
import android.view.ViewGroup;
import android.widget.*;
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
    private View message;
    private TextView content;
    private TextView name;
    private TextView date;
    private ImageView avatar;
    private LinearLayout estimateProgressBar;
    private boolean mBound = false;

    private ServiceConnection sConn = new ServiceConnection() {
        private MessagesCollectorNew.Progress mBinder;
        private Runnable refreshProgress = new Runnable() {
            @Override
            public void run() {
                if (mBinder.isReady()) {
                    int progress = mBinder.getProgress();
                    progressBar.setProgress(progress);
                    if (progress < 100) handler.postDelayed(refreshProgress, periodProgress);
                } else handler.postDelayed(refreshProgress, periodProgress);
            }
        };
        private Runnable refreshMessage = new Runnable() {
            @Override
            public void run() {
                if (mBinder.isReady()) {
                    MessageData data = mBinder.getSomeMessage();
                    if (data != null && data.id != prevId) {
                        messageContainer.setVisibility(View.VISIBLE);
                        estimateProgressBar.setVisibility(View.GONE);
                        prevId = data.id;
                        Log.d(LOG_TAG, "Data isn't null");
                        fillData(data, message);
                    }
                }
                handler.postDelayed(refreshMessage, periodMessage);
            }
        };

        @Override
        public void onServiceConnected(ComponentName componentName, final IBinder iBinder) {
            Log.d(LOG_TAG, "Service connected");
            mBinder = (MessagesCollectorNew.Progress) iBinder;
            mBound = true;
            if (mBinder.getEstimatingState()) {
                messageContainer.setVisibility(View.GONE);
                estimateProgressBar.setVisibility(View.VISIBLE);
            } else {
                messageContainer.setVisibility(View.VISIBLE);
                estimateProgressBar.setVisibility(View.GONE);
            }
            handler.post(refreshMessage);
            handler.post(refreshProgress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(LOG_TAG, "Service disconnected");
            handler.removeCallbacksAndMessages(null);
            mBound = false;
            if (sConn != null) unbindService(sConn);
            Intent intent = new Intent(LoadingActivity.this, MainPage.class);
            startActivity(intent);
            finish();
        }
    };

    private void fillData(MessageData messageData, View v) {
        content.setText(messageData.message);
        name.setText(messageData.data.name);
        date.setText(Easies.dateToHumanReadable(messageData.date));
        if (SetImage.cached.get(messageData.data.link) == null) {
            Bitmap fromMemory = CacheFile.loadPic(messageData.data.link, this);
            if (fromMemory == null) {
                CacheFile.setDefaultImage(avatar, messageData.data.type, this);
                (new SetImage(avatar, ++currentCounter, this)).execute(messageData.data.link);
            } else {
                avatar.setImageBitmap(fromMemory);
            }
        } else {
            avatar.setImageBitmap(SetImage.cached.get(messageData.data.link));
        }
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
        protected void onPostExecute(Bitmap result) {
            if (result != null && currentCounter == counter) {
                Log.d(LOG_TAG, "image has been set");
                Easies.imageViewAnimatedChange(view, result, context);
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        currentCounter = 0;
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.download_title));
        setActionBar(toolbar);
        handler = new Handler();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageContainer = (LinearLayout) findViewById(R.id.message_container);
        messageContainer.setVisibility(View.GONE);
        message = LayoutInflater.from(messageContainer.getContext())
                .inflate(R.layout.message, messageContainer, true);
        content = message.findViewById(R.id.content);
        name = message.findViewById(R.id.dialog_title);
        date = message.findViewById(R.id.date);
        avatar = message.findViewById(R.id.main_page_avatar);
        estimateProgressBar = (LinearLayout) findViewById(R.id.estimate_download);
        estimateProgressBar.setVisibility(View.VISIBLE);
        bindService(new Intent(getApplicationContext(), MessagesCollectorNew.class), sConn, 0);
        ViewGroup notToolbar = (ViewGroup) findViewById(R.id.not_toolbar);
        LayoutTransition layoutTransition = notToolbar.getLayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "Destroyed loading activity");
        if (mBound) {
            mBound = false;
            unbindService(sConn);
        }
        handler.removeCallbacksAndMessages(null);
    }
}
