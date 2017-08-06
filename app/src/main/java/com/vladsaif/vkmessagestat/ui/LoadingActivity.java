package com.vladsaif.vkmessagestat.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.*;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.AsyncParam;
import com.vladsaif.vkmessagestat.utils.Easies;

import java.io.InputStream;
import java.net.URL;

public class LoadingActivity extends AppCompatActivity {
    public final String LOG_TAG = LoadingActivity.class.getSimpleName();
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    private Handler handler;
    private final int periodMs = 500;
    private ProgressBar progressBar;
    private LinearLayout messageContainter;
    private Bitmap chatPlaceholder;
    private Bitmap otherPlaceholder;
    private int currentCounter;
    private int prevId = -1;

    private ServiceConnection sConn = new ServiceConnection() {
        private MessagesCollectorNew.Progress mBinder;
        private Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int progress = mBinder.getProgress();
                progressBar.setProgress(progress);
                MessageData data = mBinder.getSomeMessage();
                if(data != null && data.id != prevId) {
                    prevId = data.id;
                    Log.d(LOG_TAG, "Data isn't null");
                    messageContainter.removeAllViewsInLayout();
                    View message = LayoutInflater.from(messageContainter.getContext())
                            .inflate(R.layout.message, messageContainter, true);
                    fillData(data, message);
                }
                if (progress < 100) helperRecur();
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

    private void fillData(MessageData messageData, View v) {
        TextView content = v.findViewById(R.id.content);
        content.setText(messageData.message);
        TextView name = v.findViewById(R.id.dialog_title);
        name.setText(messageData.data.name);
        TextView date = v.findViewById(R.id.time);
        date.setText(Easies.dateToHumanReadable(messageData.date));
        ImageView avatar = v.findViewById(R.id.main_page_avatar);
        if(messageData.data.type == Easies.DIALOG_TYPE.CHAT) {
            avatar.setImageBitmap(chatPlaceholder);
        } else {
            avatar.setImageBitmap(otherPlaceholder);
        }
        Bitmap image = Easies.loadPic(messageData.data.link, options, getApplicationContext());
        if(image == null) {
            Log.d(LOG_TAG, "No saved image, link: " + messageData.data.link);
            (new SetImage(avatar, ++currentCounter)).execute(messageData.data.link);
        } else {
            avatar.setImageBitmap(image);
        }
    }

    class SetImage extends AsyncTask<String, Void, Bitmap> {
        private ImageView view;
        private int counter;

        public SetImage(ImageView view, int counter) {
            this.view = view;
            this.counter = counter;
        }
        @Override
        protected Bitmap doInBackground(String[] pairs) {
            String link = pairs[0];
            Bitmap bitmap = null;
            if (!link.equals("no_photo")) {
                try {
                    InputStream inputStream = new URL(link).openStream();   // Download Image from URL
                    bitmap = Easies.getCircleBitmap(BitmapFactory.decodeStream(inputStream));       // Decode Bitmap
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Easies.savePic(bitmap, Easies.transformLink(link), getApplicationContext() );
            } else {
                bitmap = null;
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null && currentCounter == counter) {
                Log.d(LOG_TAG, "image has been set");
                view.setImageBitmap(bitmap);
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
        DbHelper dbHelper = new DbHelper(this, "dialogs.db");
        chatPlaceholder = Easies.getCircleBitmap(
                BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.community_100));
        otherPlaceholder = Easies.getCircleBitmap(
                BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.camera_100));
        handler = new Handler();
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageContainter  = (LinearLayout) findViewById(R.id.message_container);
        bindService(new Intent(getApplicationContext(), MessagesCollectorNew.class), sConn, 0);
    }
}
