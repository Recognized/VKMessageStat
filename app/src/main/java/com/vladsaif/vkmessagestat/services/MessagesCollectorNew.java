package com.vladsaif.vkmessagestat.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.ui.LoadingActivity;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.Easies;
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
    private int currentOffset;
    private int messageId;

    @Override
    public void onCreate() {
        super.onCreate();
        dumper = new Dumper();
        dumper.start();
        messageId = 0;
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
                debugDrop(db);
            }
        });
        dataHandler.post(new Runnable() {
            @Override
            public void run() {
                dumper.setOnFinishCount(getLastMessageIds);
                dumper.setOnFinishLast(collectMessages);
                dumper.setOnFinishMessages(finishWork);
                dumper.setOnFinishGetDialogs(new Runnable() {
                    @Override
                    public void run() {
                        if(currentOffset < worker.dialogsCount) {
                            getDialogsFromVK.run();
                        } else {
                            dataHandler.post(getDialogs);
                            dataHandler.post(estimateDownload);
                        }
                    }
                });
                dumper.setWhenGotDialogs(whenGotDialogs);
                dumper.setWhenGotGroups(whenGotGroups);
                dumper.setWhenGotUsers(whenGotUsers);
                dumper.setNextDialog(nextDialog);
                dumper.setProcess(dumpMessagePack);
                Log.d(LOG_TAG, "All shit is set");
            }
        });
        peers = new ArrayList<>();
        access_token = VKAccessToken.currentToken().accessToken;
        currentOffset = 0;
        sendNotification();
        // REMOVE
        Log.d(LOG_TAG, access_token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Progress();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        return true;
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

    private Runnable finishWork = new Runnable() {
        @Override
        public void run() {
            sendFinishNotification();
            SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
            SharedPreferences.Editor editor = sPref.edit();
            editor.putBoolean(Strings.stat_mode, true);
            editor.apply();
            stopSelf();
        }
    };

    private void sendFinishNotification() {
        Intent notificationIntent = new Intent(this, MainPage.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        if(mNotifyManager == null) {
            mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle(getString(R.string.download_title_completed))
                .setContentText(getString(R.string.download_text_completed))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(intent)
                .setOngoing(false);
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String commandType = intent.getStringExtra(Strings.commandType);
        switch (commandType) {
            case Strings.commandDump:
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(0, 0, true).build());
                dataHandler.post(getDialogsFromVK);
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
                // TODO what should I do if you don't have any dialogs?
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
                currentMessageData = new MessageData(peer_id, first.getString("body"), first.getInt("date"),
                        worker.dialogData.get(peer_id), ++messageId);
                Log.d(LOG_TAG, "Message: " + currentMessageData.message);
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

    public Runnable getDialogsFromVK = new Runnable() {
        @Override
        public void run() {
            Message m = requestHandler.obtainMessage();
            m.what = VKWorker.GET_DIALOGS;
            m.arg1 = 200;
            m.arg2 = currentOffset;
            currentOffset += 200;
            requestHandler.sendMessage(m);
        }
    };

    public OneArg nextDialog = new OneArg() {
        @Override
        public void call(final int peer_id) {
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

    public MessagesCollectorNew.ResponseWork whenGotDialogs = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            Log.d(LOG_TAG, "WHEN GOT DIALOGS");
            ArrayList<Integer> user_ids = new ArrayList<>(), group_ids = new ArrayList<>();
            try {
                Log.d(LOG_TAG, "DIalogs collecting");
                JSONArray items = response.json.getJSONObject("response").getJSONArray("items");
                worker.dialogsCount = response.json.getJSONObject("response").getInt("count");
                for (int i = 0; i < items.length(); ++i) {
                    JSONObject message = items.getJSONObject(i).getJSONObject("message");
                    int chat_id = message.has("chat_id") ? message.getInt("chat_id") : -1;
                    int user_id = message.getInt("user_id");
                    Easies.DIALOG_TYPE type = Easies.resolveTypeBySomeShitThankYouVK(user_id, chat_id);
                    int dialog_id = Easies.getDialogID(type, user_id, chat_id);
                    worker.time.put(dialog_id, message.getInt("date"));
                    switch (type) {
                        case CHAT:
                            ContentValues val = new ContentValues();
                            val.put(Strings.dialog_id, dialog_id);
                            val.put(Strings.type, Strings.chat);
                            val.put(Strings.date, worker.time.get(dialog_id));
                            db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                            ContentValues cv = new ContentValues();
                            cv.put(Strings.dialog_id, dialog_id);
                            cv.put(Strings.name, message.getString("title"));
                            db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            ContentValues con = new ContentValues();
                            con.put(Strings.dialog_id, dialog_id);
                            String link = message.has("photo_100") ? message.getString("photo_100") : Strings.no_photo;
                            con.put(Strings.link, link);
                            db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);

                            DialogData chatData = new DialogData(dialog_id, Easies.DIALOG_TYPE.CHAT);
                            chatData.link = link;
                            chatData.name = message.getString("title");
                            worker.dialogData.put(dialog_id, chatData);
                            break;
                        case USER:
                            user_ids.add(dialog_id);
                            break;

                        case COMMUNITY:
                            group_ids.add(-dialog_id);
                    }
                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            if(user_ids.size() > 0)
            {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_USERS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("user_ids", user_ids);
                m.setData(b);
                requestHandler.sendMessage(m);
            } else {
                dumper.gotUsers = true;
            }
            if(group_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_GROUPS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("group_ids", user_ids);
                m.setData(b);
                requestHandler.sendMessage(m);
            } else {
                dumper.gotGroups = true;
            }
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotUsers = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            try {
                JSONArray users = response.json.getJSONArray("response");
                for (int i = 0; i < users.length(); ++i) {
                    ContentValues val = new ContentValues();
                    JSONObject user = users.getJSONObject(i);
                    int dialog_id = user.getInt(Strings.id);
                    val.put(Strings.dialog_id, dialog_id);
                    val.put(Strings.type, Strings.user);
                    val.put(Strings.date, worker.time.get(dialog_id));
                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                    String username = user.getString("first_name") + " " + user.getString("last_name");
                    ContentValues cv = new ContentValues();
                    cv.put(Strings.dialog_id, dialog_id);
                    cv.put(Strings.name, username);
                    db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues con = new ContentValues();
                    con.put(Strings.dialog_id, dialog_id);
                    String link = user.has("photo_100") ? user.getString("photo_100") : Strings.no_photo;
                    con.put(Strings.link, link);
                    db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);

                    DialogData userData = new DialogData(dialog_id, Easies.DIALOG_TYPE.CHAT);
                    userData.link = link;
                    userData.name = username;
                    worker.dialogData.put(dialog_id, userData);
                }

            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotGroups = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            try {
                JSONArray array = response.json.getJSONObject("response").getJSONArray("items");
                for (int i = 0; i < array.length(); ++i) {
                    ContentValues val = new ContentValues();
                    JSONObject jj = array.getJSONObject(i).getJSONObject("message");
                    int id = -jj.getInt("id");
                    val.put(Strings.dialog_id, id);
                    val.put(Strings.type, Strings.community);
                    val.put(Strings.date, worker.time.get(id));
                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues cv = new ContentValues();
                    cv.put(Strings.dialog_id, id);
                    cv.put(Strings.name, array.getJSONObject(i).getString("name"));
                    db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues con = new ContentValues();
                    con.put(Strings.dialog_id, id);
                    String link = jj.has("photo_100") ? jj.getString("photo_100") : Strings.no_photo;
                    con.put(Strings.link, link);
                    db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);

                    DialogData groupData = new DialogData(id, Easies.DIALOG_TYPE.CHAT);
                    groupData.link = link;
                    groupData.name = array.getJSONObject(i).getString("name");
                    worker.dialogData.put(id, groupData);

                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };
}
