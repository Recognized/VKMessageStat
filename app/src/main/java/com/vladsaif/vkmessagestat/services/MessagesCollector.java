package com.vladsaif.vkmessagestat.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

public class MessagesCollector extends Service {
    private static final int fixedDelayMs = 1000;
    private static final String LOG_TAG = "MyService";
    private static int requests = 0;
    private static long prev_request = 0;
    private final DbHelper dbHelper;
    private final SQLiteDatabase db;
    private final int NOTIFICATION_ID = 42;
    private final int packSize = 3980;
    private Handler mainHandler;
    private String access_token;
    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private int progress;

    public MessagesCollector() {
        dbHelper = new DbHelper(getApplicationContext(), Strings.dialogs);
        db = dbHelper.getWritableDatabase();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("MessagesCollector", HandlerThread.NORM_PRIORITY - 1);
        thread.start();
        mainHandler = new Handler(thread.getLooper());
        access_token = VKAccessToken.currentToken().accessToken;
        sendNotification();
        Log.d(LOG_TAG, access_token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public interface ResponseWork {
        int doWork(VKResponse response);
    }

    public interface DumpCallback {
        void onComplete(int new_start_message_id);
    }

    public final class DumpMessagePack implements Runnable {
        private int peer_id;
        private int start_message_id;
        private String tableName;
        private DumpCallback finishCallback;
        private ResponseWork worker = new ResponseWork() {
            @Override
            public int doWork(VKResponse response) {
                try {
                    JSONObject res = response.json.getJSONObject("response");
                    JSONArray messages = res.getJSONArray("result");
                    int skipped = res.getJSONArray("result").getJSONObject(0).getInt("skipped");
                    db.beginTransaction();
                    for (int i = 0; i < messages.length(); ++i) {
                        JSONArray msg = messages.getJSONObject(i).getJSONArray("items");
                        for (int j = 0; j < msg.length(); ++j) {
                            JSONObject js = msg.getJSONObject(j);
                            ContentValues cv = new ContentValues();
                            cv.put(Strings.message_id, js.getInt(Strings.id));
                            cv.put(Strings.body, js.getString(Strings.body));
                            cv.put(Strings.date, js.getInt(Strings.date));
                            db.insertWithOnConflict(tableName, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    db.endTransaction();
                    progress += packSize;
                    return  skipped == 0 ? -1 : response.json.getInt("new_start");
                } catch (JSONException ex) {
                    Log.e(LOG_TAG, ex.toString());
                    return -1;
                }
            }
        };

        private DumpCallback dumpCallback = new DumpCallback() {
            @Override
            public void onComplete(int new_start_message_id) {
                if (new_start_message_id < 0) finishCallback.onComplete(-1);
                    getMessages(new_start_message_id);
            }
        };


        public DumpMessagePack(int peer_id, int start_message_id, DumpCallback finishCallback) {
            this.tableName = Strings.prefix_messages + Integer.toString(peer_id);
            db.execSQL("CREATE TABLE IF NOT EXISTS " + tableName +
                    " (message_id INTEGER PRIMARY KEY ON CONFLICT REPLACE, body TEXT, date INT);");
            this.peer_id = peer_id;
            this.start_message_id = start_message_id;
            this.finishCallback = finishCallback;
        }

        @Override
        public void run() {
            getMessages(start_message_id);
        }

        void getMessages(int start_message_id) {
            CountedRequest("execute.getMessages", VKParameters.from(Strings.access_token, access_token,
                    Strings.peer_id, Integer.toString(peer_id), Strings.start_message_id,
                    Integer.toString(start_message_id)), worker, dumpCallback);
        }
    }

    public final class LastMessageId implements Runnable {
        private int peer_id;
        private DumpCallback callback;
        private ResponseWork resolveId = new ResponseWork() {
            @Override
            public int doWork(VKResponse response) {
                try {
                    return response.json.getJSONObject("response")
                            .getJSONArray("items")
                            .getJSONObject(0)
                            .getInt(Strings.id);
                } catch (JSONException ex) {
                    Log.e(LOG_TAG, ex.toString());
                    return -1;
                }
            }
        };
        private DumpCallback dumpCallback = new DumpCallback() {
            @Override
            public void onComplete(int last_message_id) {
                ContentValues cv = new ContentValues();
                cv.put(Strings.last_message_id, last_message_id);
                cv.put(Strings.dialog_id, peer_id);
                db.insertWithOnConflict(Strings.last_message_id, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                callback.onComplete(last_message_id);
            }
        };

        public LastMessageId(int peer_id, DumpCallback callback) {
            this.peer_id = peer_id;
            this.callback = callback;
        }
        @Override
        public void run() {
            Cursor cursor = db.rawQuery("SELECT " + Strings.message_id + " FROM " + Strings.last_message_id +
                    " WHERE " + Strings.dialog_id  + "=" + Integer.toString(peer_id), new String[]{});
            if (cursor.moveToFirst()) {
                int anInt = cursor.getInt(cursor.getColumnIndex(Strings.last_message_id));
                cursor.close();
                callback.onComplete(anInt);
            } else {
                cursor.close();
                CountedRequest("messages.getHistory", VKParameters.from(Strings.peer_id, Integer.toString(peer_id),
                        Strings.rev, "1"), resolveId, dumpCallback);
            }
        }
    }

    private void CountedRequest(final String method, final VKParameters param,
                                final ResponseWork work, final DumpCallback callback) {
        requests++;
        if (prev_request == 0) {
            prev_request = new Date().getTime();
        }
        long delay = Math.max(0, fixedDelayMs - (new Date().getTime() - prev_request));
        Runnable task = new Runnable() {
            @Override
            public void run() {
                VKRequest req = new VKRequest(method, param);
                req.executeWithListener(new VKRequest.VKRequestListener() {
                    @Override
                    public void onComplete(VKResponse response) {
                        super.onComplete(response);
                        callback.onComplete(work.doWork(response));
                        requests--;
                    }
                });
            }
        };
        if (delay == 0) {
            mainHandler.post(task);
        } else {
            mainHandler.postDelayed(task, delay);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String commandType = intent.getStringExtra(Strings.commandType);
        switch (commandType) {
            case Strings.commandDump:
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(0, 0, true).build());
                estimateDownload();
                progress = 0;
                mainHandler.post(collectAllMessages);
                break;
            // TODO
        }
        return START_NOT_STICKY;
    }

    private Runnable collectAllMessages = new Runnable() {
        @Override
        public void run() {
            final Cursor cursor = db.rawQuery("SELECT " + Strings.dialog_id + " FROM " + Strings.dialogs + ";", new String[]{});
            final int peerIndex = cursor.getColumnIndex(Strings.dialog_id);
            if(cursor.moveToFirst()) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final int peer_id = cursor.getInt(peerIndex);
                        LoopAndCollect(peer_id, peerIndex, cursor);
                    }
                });
            }
        }
    };

    private void LoopAndCollect(final int peer_id, final int columnIndex, final Cursor cursor) {
        mainHandler.post(new LastMessageId(peer_id, new DumpCallback() {
            @Override
            public void onComplete(int start_message_id) {
                mainHandler.post(new DumpMessagePack(peer_id, start_message_id, new DumpCallback() {
                    @Override
                    public void onComplete(int not_used) {
                        if(cursor.moveToNext()) {
                            LoopAndCollect(cursor.getInt(columnIndex), columnIndex, cursor);
                        } else {
                            cursor.close();
                        }
                    }
                }));
            }
        }
        ));
    }

    private void sendNotification() {
        Intent notificationIntent = new Intent(this, LoadingActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle(getString(R.string.download_title))
                .setContentText(getString(R.string.download_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(intent);
    }

    private void estimateDownload() {
        var arg1 = parseInt(Args.arg1);
        var arg2 = parseInt(Args.arg2);
        var arg3 = parseInt(Args.arg3);
        var arg4 = parseInt(Args.arg4);
        var arg5 = parseInt(Args.arg5);
        var arg6 = parseInt(Args.arg6);
        var arg7 = parseInt(Args.arg7);
        var arg8 = parseInt(Args.arg8);
        var arg9 = parseInt(Args.arg9);
        var arg10 =parseInt(Args.arg10);
        var arg11 =parseInt(Args.arg11);
        var arg12 =parseInt(Args.arg12);
        var arg13 =parseInt(Args.arg13);
        var arg14 =parseInt(Args.arg14);
        var arg15 =parseInt(Args.arg15);
        var arg16 =parseInt(Args.arg16);
        var arg17 =parseInt(Args.arg17);
        var arg18 =parseInt(Args.arg18);
        var arg19 =parseInt(Args.arg19);
        var arg20 =parseInt(Args.arg20);

        if(arg1  = parseInt(Args.arg1);
        if(arg2  = parseInt(Args.arg2);
        if(arg3  = parseInt(Args.arg3);
        if(arg4  = parseInt(Args.arg4);
        if(arg5  = parseInt(Args.arg5);
        if(arg6  = parseInt(Args.arg6);
        if(arg7  = parseInt(Args.arg7);
        if(arg8  = parseInt(Args.arg8);
        if(arg9  = parseInt(Args.arg9);
        if(arg10  =parseInt(Args.arg10);
        if(arg11  =parseInt(Args.arg11);
        if(arg12  =parseInt(Args.arg12);
        if(arg13  =parseInt(Args.arg13);
        if(arg14  =parseInt(Args.arg14);
        if(arg15  =parseInt(Args.arg15);
        if(arg16  =parseInt(Args.arg16);
        if(arg17  =parseInt(Args.arg17);
        if(arg18  =parseInt(Args.arg18);
        if(arg19  =parseInt(Args.arg19);
        if(arg20  =parseInt(Args.arg20);


    }

}
