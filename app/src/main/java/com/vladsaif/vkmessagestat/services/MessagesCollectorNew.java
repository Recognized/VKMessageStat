package com.vladsaif.vkmessagestat.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.ui.LoadingActivity;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MessagesCollectorNew extends Service {
    private static final int fixedDelayMs = 1000;
    private static final String LOG_TAG = "MyService";
    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private final int NOTIFICATION_ID = 42;
    private final int packSize = 25 * 199;
    private String access_token;
    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private int progress;
    private MessageData currentMessageData;
    private VKWorker worker;
    private Dumper dumper;
    private Handler requestHandler;
    private Handler dataHandler;
    private ArrayList<Integer> peers;
    private ArrayList<Integer> dialogs;

    @Override
    public void onCreate() {
        super.onCreate();
        dumper = new Dumper();
        dumper.start();
        while (dumper.dumper == null) ;
        worker = new VKWorker(dumper.dumper, access_token);
        worker.start();
        while (worker.mHandler == null) ;
        requestHandler = worker.mHandler;
        dataHandler = dumper.dumper;
        dataHandler.post(new Runnable() {
            @Override
            public void run() {
                dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
                db = dbHelper.getWritableDatabase();
            }
        });
        dataHandler.post(getDialogs);
        dataHandler.post(new Runnable() {
            @Override
            public void run() {
                dumper.setOnFinishCount(getLastMessageIds);
                dumper.setOnFinishLast(collectMessages);
                dumper.setOnFinishMessages(finishWork);
                dumper.setNextDialog(nextDialog);
                dumper.setProcess(dumpMessagePack);
            }
        });
        peers = new ArrayList<>();
        access_token = VKAccessToken.currentToken().accessToken;
        sendNotification();
        // REMOVE
        debugDrop(db);
        Log.d(LOG_TAG, access_token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Progress();
    }

    public final class Progress extends Binder {
        public int getProgress() {
            return Math.round((float) progress / (float) worker.allMessages * 100);
        }

        public MessageData getSomeMessage() {
            return currentMessageData;
        }
    }

    public interface ResponseWork {
        int doWork(VKResponse response, int peer_id, int currentProgress);
    }

    // TODO what to do if work has been finished?
    private Runnable finishWork = new Runnable() {
        @Override
        public void run() {
            // TODO
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String commandType = intent.getStringExtra(Strings.commandType);
        switch (commandType) {
            case Strings.commandDump:
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(0, 0, true).build());
                dataHandler.post(estimateDownload);
                progress = 0;
                break;
            // TODO
        }
        return START_NOT_STICKY;
    }

    public Runnable estimateDownload = new Runnable() {
        @Override
        public void run() {
            for (Integer peer : dialogs) {
                Cursor count = db.rawQuery("SELECT " + Strings.counter + " FROM " + Strings.counts +
                        " WHERE " + Strings.dialog_id + " = ?;", new String[]{Integer.toString(peer)});
                if (count.moveToFirst()) {
                    worker.existingMessages.put(peer, count.getInt(count.getColumnIndex(Strings.counter)));
                } else {
                    worker.existingMessages.put(peer, 0);
                    peers.add(peer);
                }
                count.close();
            }
            if (!peers.isEmpty()) {
                ArrayDeque<Integer> d = new ArrayDeque<>();
                for (Integer i : peers) {
                    d.addLast(i);
                }
                dumper.setQueue(d);
                dumper.expect_count = peers.size() / 25 + (peers.size() % 25 == 0 ? 0 : 1);
                messageToCountOrLast(peers, VKWorker.GET_COUNT);
            } else {
                // TODO what should i do i you don't have any dialogs?
            }
        }
    };

    public void messageToCountOrLast(ArrayList<Integer> src, int flag) {
        int i = 0;
        do {
            Message m = requestHandler.obtainMessage();
            m.what = flag;
            Bundle b = new Bundle();
            b.putIntegerArrayList("peers",
                    new ArrayList<>(src.subList(i, Math.min(i + 25, src.size()))));
            m.setData(b);
            requestHandler.sendMessage(m);
            i += 25;
        } while (i < src.size());
    }

    private Runnable getLastMessageIds = new Runnable() {
        @Override
        public void run() {
            ArrayList<Integer> needToCollect = new ArrayList<>();
            for (Integer dialog : dialogs) {
                Cursor cursor = db.rawQuery("SELECT " + Strings.message_id + " FROM " + Strings.last_message_id +
                        " WHERE " + Strings.dialog_id + "=" + Integer.toString(dialog), new String[]{});
                if (cursor.moveToFirst()) {
                    worker.lastMessageIds.put(dialog, cursor.getInt(cursor.getColumnIndex(Strings.message_id)));
                } else {
                    needToCollect.add(dialog);
                }
                cursor.close();
            }
            if (!needToCollect.isEmpty()) {
                dumper.expect_last = needToCollect.size() / 25 + (needToCollect.size() % 25 == 0 ? 0 : 1);
                messageToCountOrLast(needToCollect, VKWorker.GET_LAST);
            }
        }
    };

    private Runnable collectMessages = new Runnable() {
        @Override
        public void run() {
            Message m = dataHandler.obtainMessage();
            Bundle b = new Bundle();
            m.setData(b);
            m.what = VKWorker.BEGIN_COLLECTING;
            dataHandler.sendMessage(m);
        }
    };

    private ResponseWork dumpMessagePack = new ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            try {
                int diff = worker.realMessages.get(peer_id) - worker.existingMessages.get(peer_id);
                String tableName = Strings.prefix_messages + Integer.toString(peer_id);
                db.execSQL("CREATE TABLE IF NOT EXISTS " + tableName +
                        " (message_id INTEGER PRIMARY KEY, body TEXT, date INT);");
                JSONObject res = response.json.getJSONObject("response");
                JSONArray messages = res.getJSONArray("result");
                int skipped = 0;
                if (res.getJSONArray("result").getJSONObject(0).has("skipped")) {
                    skipped = res.getJSONArray("result").getJSONObject(0).getInt("skipped");
                }
                int id = -1;
                db.beginTransaction();
                for (int i = 0; i < messages.length(); ++i) {
                    JSONArray msg = messages.getJSONObject(i).getJSONArray("items");
                    for (int j = 0; j < msg.length(); ++j) {
                        JSONObject js = msg.getJSONObject(j);
                        ContentValues cv = new ContentValues();
                        id = Math.max(id, js.getInt(Strings.id));
                        cv.put(Strings.message_id, id);
                        cv.put(Strings.body, js.getString(Strings.body));
                        cv.put(Strings.date, js.getInt(Strings.date));
                        db.insertWithOnConflict(tableName, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
                JSONObject first = response.json.getJSONObject("response")
                        .getJSONArray("result")
                        .getJSONObject(0)
                        .getJSONArray("items")
                        .getJSONObject(0);
                currentMessageData = new MessageData(peer_id, first.getString("body"), first.getInt("date"));
                ContentValues d = new ContentValues();
                d.put(Strings.message_id, id);
                d.put(Strings.dialog_id, peer_id);
                db.insertWithOnConflict(Strings.last_message_id, null, d, SQLiteDatabase.CONFLICT_REPLACE);
                currentProgress = Math.min(currentProgress + packSize, diff);
                ContentValues s = new ContentValues();
                s.put(Strings.counter, currentProgress + worker.existingMessages.get(peer_id));
                s.put(Strings.dialog_id, peer_id);
                db.insertWithOnConflict(Strings.counts, null, s, SQLiteDatabase.CONFLICT_REPLACE);
                db.endTransaction();
                progress += Math.min(packSize, diff - currentProgress);
                Log.d(LOG_TAG, "Progress " + Integer.toString(progress));
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(worker.allMessages, progress, false).build());
                if (skipped == 0) {
                    return -1;
                } else {
                    int new_start = response.json.getJSONObject("response").getInt("new_start");
                    sendMessageGetMessagePack(peer_id, new_start);
                    return 1;
                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
                return -1;
            }
        }
    };

    public void sendMessageGetMessagePack(int peer_id, int new_start) {
        Message m = requestHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt(Strings.peer_id, peer_id);
        b.putInt(Strings.start_message_id, new_start);
        m.setData(b);
        m.what = VKWorker.GET_MESSAGES;
        requestHandler.sendMessage(m);
    }

    public interface OneArg {
        void call(int arg);
    }

    public OneArg nextDialog = new OneArg() {
        @Override
        public void call(int peer_id) {
            sendMessageGetMessagePack(peer_id, worker.lastMessageIds.get(peer_id));
        }
    };

    public Runnable getDialogs = new Runnable() {
        @Override
        public void run() {
            dialogs = new ArrayList<>();
            final Cursor cursor = db.rawQuery("SELECT dialog_id, type, date FROM dialogs ORDER BY date DESC;", new String[]{});
            int columnIndex = cursor.getColumnIndex(Strings.dialog_id);
            if (cursor.moveToFirst()) {
                do {
                    dialogs.add(cursor.getInt(columnIndex));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    };

    public void debugDrop(SQLiteDatabase data) {
        data.execSQL("DROP TABLE IF EXISTS " + Strings.last_message_id + ";");
        data.execSQL("DROP TABLE IF EXISTS " + Strings.counts + ";");
        dbHelper.onCreate(data);
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
                .setContentIntent(intent)
                .setOngoing(true);
    }
}
