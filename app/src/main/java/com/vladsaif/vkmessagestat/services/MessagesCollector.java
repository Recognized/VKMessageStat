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
import android.os.Handler;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.SparseIntArray;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.ui.LoadingActivity;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MessagesCollector extends Service {
    private static final int fixedDelayMs = 1000;
    private static final String LOG_TAG = "MyService";
    private static int requests = 0;
    private static long prev_request = 0;
    private DbHelper dbHelper;
    private SQLiteDatabase db;
    private final int NOTIFICATION_ID = 42;
    private final int packSize = 25 * 199;
    private Handler mainHandler;
    private HandlerThread thread;
    private String access_token;
    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private int progress;
    private int allMessages;
    private SparseIntArray existingMessages;
    private SparseIntArray realMessages;
    private long startTime;
    private MessageData currentMessageData;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
        db = dbHelper.getWritableDatabase();
        thread = new HandlerThread("MessagesCollector", HandlerThread.NORM_PRIORITY - 1);
        thread.start();
        mainHandler = new Handler(thread.getLooper());
        access_token = VKAccessToken.currentToken().accessToken;
        startTime = new Date().getTime();
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
            return Math.round((float) progress / (float) allMessages * 100);
        }
        public MessageData getSomeMessage() {
            return currentMessageData;
        }
    }

    public interface ResponseWork {
        int doWork(VKResponse response);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String commandType = intent.getStringExtra(Strings.commandType);
        switch (commandType) {
            case Strings.commandDump:
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(0, 0, true).build());
                mainHandler.post(new EstimateDownload(new DumpCallback() {
                    @Override
                    public void onComplete(int new_start_message_id) {
                        Log.d(LOG_TAG, "collecting has began");
                        mainHandler.post(collectAllMessages);
                    }
                }));
                progress = 0;
                break;
            // TODO
        }
        return START_NOT_STICKY;
    }
    private final class EstimateDownload implements Runnable {
        private final DumpCallback callback;
        private ArrayList<Integer> peers;
        public EstimateDownload(DumpCallback callback) {
            this.callback = callback;
            allMessages = 0;
            peers = new ArrayList<>();
            existingMessages = new SparseIntArray();
            realMessages = new SparseIntArray();
            final Cursor cursor = db.rawQuery("SELECT dialog_id, type, date FROM dialogs;", new String[]{});
            int columnIndex = cursor.getColumnIndex(Strings.dialog_id);
            if (cursor.moveToFirst()) {
                do {
                    int peer = cursor.getInt(columnIndex);
                    Cursor count = db.rawQuery("SELECT " + Strings.counter + " FROM " + Strings.counts +
                            " WHERE " + Strings.dialog_id + " = ?;", new String[]{Integer.toString(peer)});
                    if (count.moveToFirst()) {
                        existingMessages.put(peer, count.getInt(count.getColumnIndex(Strings.counter)));
                    } else {
                        existingMessages.put(peer, 0);
                        peers.add(peer);
                    }
                    count.close();
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        @Override
        public void run() {
            recurHelper(0);
        }

        public void recurHelper(final int index_start) {
            if (index_start < 0 || peers.size() == 0) {
                mNotifyManager.notify(NOTIFICATION_ID,
                        mBuilder.setProgress(allMessages, 0, false).build());
                callback.onComplete(1);
            } else CountedRequest("execute.getCount", peersToParam(peers, index_start), worker, new DumpCallback() {
                @Override
                public void onComplete(int new_start_message_id) {
                    if (new_start_message_id < 0) throw new RuntimeException("Json exception");
                    if (index_start + 25 < peers.size()) recurHelper(index_start + 25);
                    else recurHelper(-1);
                }
            });
        }

        private ResponseWork worker = new ResponseWork() {
            @Override
            public int doWork(VKResponse response) {
                try {
                    JSONArray array = response.json.getJSONArray("response");
                    for (int i = 0; i < array.length(); ++i) {
                        JSONObject obj = array.getJSONObject(i);
                        int peer = obj.getInt("peer_id");
                        int count = obj.getInt("count");
                        realMessages.put(peer, count);
                        allMessages += count - existingMessages.get(peer);
                    }
                    return 1;
                } catch (JSONException ex) {
                    Log.e(LOG_TAG, ex.toString() + '\n' + response.json.toString());
                    return -1;
                }
            }
        };
    }

    private Runnable collectAllMessages = new Runnable() {
        @Override
        public void run() {
            final Cursor cursor = db.rawQuery("SELECT " + Strings.dialog_id +
                    " FROM " + Strings.dialogs +
                    " ORDER BY date DESC;", new String[]{});
            final int peerIndex = cursor.getColumnIndex(Strings.dialog_id);
            if (cursor.moveToFirst()) {
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
        Log.d(LOG_TAG, "Looping for: " + Integer.toString(peer_id));
        mainHandler.post(new LastMessageId(peer_id, new DumpCallback() {
            @Override
            public void onComplete(int start_message_id) {
                mainHandler.post(new DumpMessagePack(peer_id, start_message_id, new DumpCallback() {
                    @Override
                    public void onComplete(int not_used) {
                        Log.d(LOG_TAG, "Dump for " + Integer.toString(peer_id) + " finished.");
                        if (cursor.moveToNext()) {
                            LoopAndCollect(cursor.getInt(columnIndex), columnIndex, cursor);
                        } else {
                            cursor.close();
                            stopSelf();
                        }
                    }
                }));
            }
        }
        ));
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
                Log.d(LOG_TAG, "Last message id found: " + Integer.toString(last_message_id));
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
                    " WHERE " + Strings.dialog_id + "=" + Integer.toString(peer_id), new String[]{});
            if (cursor.moveToFirst()) {
                int anInt = cursor.getInt(cursor.getColumnIndex(Strings.message_id));
                cursor.close();
                Log.d(LOG_TAG, "Last message id " + Integer.toString(anInt));
                callback.onComplete(anInt);
            } else {
                cursor.close();
                Log.d(LOG_TAG, "Last message id not found");
                CountedRequest("messages.getHistory", VKParameters.from("user_id", Integer.toString(peer_id),
                        Strings.rev, "1"), resolveId, dumpCallback);
            }
        }

    }

    public final class DumpMessagePack implements Runnable {
        private int peer_id;
        private int start_message_id;
        private int currentProgress;
        private int diff;
        private String tableName;

        private DumpCallback finishCallback;


        private ResponseWork worker = new ResponseWork() {
            @Override
            public int doWork(VKResponse response) {
                try {
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
                    s.put(Strings.counter, currentProgress + existingMessages.get(peer_id));
                    s.put(Strings.dialog_id, peer_id);
                    db.insertWithOnConflict(Strings.counts, null, s, SQLiteDatabase.CONFLICT_REPLACE);
                    db.endTransaction();
                    progress += Math.min(packSize, diff - currentProgress);
                    Log.d(LOG_TAG, "Progress " + Integer.toString(progress));
                    mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(allMessages, progress, false).build());
                    return skipped == 0 ? -1 : response.json.getJSONObject("response").getInt("new_start");
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
                    " (message_id INTEGER PRIMARY KEY, body TEXT, date INT);");
            this.currentProgress = 0;
            this.peer_id = peer_id;
            this.start_message_id = start_message_id;
            this.finishCallback = finishCallback;
            this.diff = realMessages.get(peer_id) - existingMessages.get(peer_id);
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

    public interface DumpCallback {

        void onComplete(int new_start_message_id);
    }

    private void CountedRequest(final String method, final VKParameters param,
                                final ResponseWork work, final DumpCallback callback) {
        requests++;
        if (prev_request == 0) {
            prev_request = new Date().getTime();
        }
        long cur = new Date().getTime();
        long delay = Math.max(0, fixedDelayMs - (cur - prev_request));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Log.e(LOG_TAG, ex.toString());
        }
        Log.d("timing", Long.toString((new Date().getTime() - startTime)));
        final VKRequest req = new VKRequest(method, param);
        prev_request = cur + delay;
        Runnable task = new Runnable() {
            @Override
            public void run() {
                req.executeWithListener(new VKRequest.VKRequestListener() {
                    @Override
                    public void onError(VKError error) {
                        super.onError(error);
                        throw new RuntimeException(error.toString());
                    }

                    @Override
                    public void onComplete(VKResponse response) {
                        super.onComplete(response);
                        prev_request = new Date().getTime();
                        Log.d(LOG_TAG, "Request received");
                        callback.onComplete(work.doWork(response));
                        requests--;
                    }
                });
            }
        };
        if (delay == 0) mainHandler.post(task);
        else mainHandler.postDelayed(task, delay);
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

    private VKParameters peersToParam(ArrayList<Integer> peers, int index_start) {
        // Achtung!! Extreme chance of making mistake, but it is Java and I can't do it better;
        String[] filteredPeers = new String[25];
        for (int i = index_start; i < Math.min(peers.size(), index_start + 25); ++i) {
            filteredPeers[i - index_start] = Integer.toString(peers.get(i));
        }
        for (int i = Math.min(index_start + 25, peers.size()); i < 25; ++i) {
            filteredPeers[i - index_start] = "-1";
        }
        for (String s : filteredPeers) {
            Log.d(LOG_TAG, s);
        }
        return VKParameters.from(
                Strings.access_token, access_token,
                "arg1", filteredPeers[0],
                "arg2", filteredPeers[1],
                "arg3", filteredPeers[2],
                "arg4", filteredPeers[3],
                "arg5", filteredPeers[4],
                "arg6", filteredPeers[5],
                "arg7", filteredPeers[6],
                "arg8", filteredPeers[7],
                "arg9", filteredPeers[8],
                "arg10", filteredPeers[9],
                "arg11", filteredPeers[10],
                "arg12", filteredPeers[11],
                "arg13", filteredPeers[12],
                "arg14", filteredPeers[13],
                "arg15", filteredPeers[14],
                "arg16", filteredPeers[15],
                "arg17", filteredPeers[16],
                "arg18", filteredPeers[17],
                "arg19", filteredPeers[18],
                "arg20", filteredPeers[19],
                "arg21", filteredPeers[20],
                "arg22", filteredPeers[21],
                "arg23", filteredPeers[22],
                "arg24", filteredPeers[23],
                "arg25", filteredPeers[24]
        );
    }

    public void debugDrop(SQLiteDatabase data) {
        data.execSQL("DROP TABLE IF EXISTS " + Strings.last_message_id + ";");
        data.execSQL("DROP TABLE IF EXISTS " + Strings.counts + ";");
        dbHelper.onCreate(data);
    }


}
