package com.vladsaif.vkmessagestat.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.db.GlobalData;
import com.vladsaif.vkmessagestat.db.MessageData;
import com.vladsaif.vkmessagestat.ui.LoadingActivity;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.DataManager;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static com.vladsaif.vkmessagestat.utils.Easies.deserializeData;
import static com.vladsaif.vkmessagestat.utils.Easies.serializeData;

public class MessagesCollectorNew extends Service {
    private static final String LOG_TAG = "MyService";
    private final int NOTIFICATION_ID = 42;
    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private int progress;
    private MessageData currentMessageData;
    private PreparedThreads preparedThreads;
    private VKWorker worker;
    private Dumper dumper;
    private Handler requestHandler;
    private Handler dataHandler;
    private ArrayList<Integer> dialogs;
    private int currentOffset = 0;
    private int messageId = 0;
    private DataManager dataManager;
    private boolean estimating = true;
    private boolean preparedThreadsReady = false;
    private boolean runOnlyOnceWhenConnectionLost = true;
    private Runnable handlersDereference = new Runnable() {
        @Override
        public void run() {
            worker = preparedThreads.worker;
            dumper = preparedThreads.dumper;
            requestHandler = preparedThreads.requestHandler;
            dataHandler = preparedThreads.dataHandler;
        }
    };
    private ConnectivityManager connectivityManager;
    private ArrayList<Integer> needToKnowUsernames = new ArrayList<>();
    private ArrayList<Integer> currentDialogMessagesTime;
    private PowerManager.WakeLock wl;
    private boolean notifications;
    private boolean refreshingFinished = false;
    public static boolean serviceRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
        dataManager = new DataManager(getApplicationContext());
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

    @Override
    public void onDestroy() {
        if (worker != null) {
            serializeData(worker.dialogData, this);
            if (worker.globalData != null) worker.globalData.serializeThis(this);
        }
        serviceRunning = false;
    }

    public final class Progress extends Binder {
        public int getProgress() {
            return Math.round((float) progress / (float) worker.allMessages * 100);
        }

        public MessageData getSomeMessage() {
            return currentMessageData;
        }

        public boolean getEstimatingState() {
            return estimating;
        }

        public boolean isReady() {
            return preparedThreadsReady;
        }

        public boolean isRefreshingFinished() {
            return refreshingFinished;
        }
    }

    public interface ResponseWork {
        int doWork(VKResponse response, int peer_id);
    }

    private void sendFinishNotification() {
        Intent notificationIntent = new Intent(this, MainPage.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        if (mNotifyManager == null) {
            mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle(getString(R.string.download_title_completed))
                .setContentText(getString(R.string.download_text_completed))
                .setSmallIcon(R.mipmap.service_icon)
                .setContentIntent(intent)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.service_icon))
                .setOngoing(false);
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String commandType = intent.getStringExtra(Strings.commandType);
        serviceRunning = true;
        switch (commandType) {
            case Strings.commandDump:
                wl.acquire();
                sendNotification();
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(0, 0, true).build());
                notifications = true;
                break;
            case Strings.commandRefresh:
                notifications = false;
                // TODO
        }
        preparedThreads = new PreparedThreads(dumperInitialization, handlersDereference, getDialogsFromVK, getApplicationContext());
        progress = 0;
        return START_NOT_STICKY;
    }

    private void messageToCountOrLast(ArrayList<Integer> src, int flag) {
        int i = 0;
        VKWorker.PackRequest packRequest = new VKWorker.PackRequest(requestHandler);
        do {
            Log.d(LOG_TAG, "Send list " + Integer.toString(i));
            Message m = requestHandler.obtainMessage();
            m.what = flag;
            Bundle b = new Bundle();
            b.putIntegerArrayList("peers",
                    new ArrayList<>(src.subList(i, Math.min(i + 25, src.size()))));
            m.setData(b);
            packRequest.addMessage(m);
            i += 25;
        } while (i < src.size());
        packRequest.finishAdding();
    }

    private void setQueueToUpdate() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Integer dialog : dialogs) {
            if (worker.realMessages.get(dialog) - worker.dialogData.get(dialog).messages != 0) {
                Log.d(LOG_TAG, "dialog added");
                queue.addFirst(dialog);
            }
        }
        dumper.setQueue(queue);
    }

    private Runnable finishWork = new Runnable() {
        @Override
        public void run() {
            serializeData(worker.dialogData, getApplicationContext());
            worker.globalData.serializeThis(getApplicationContext());
            if (notifications) {
                sendFinishNotification();
                SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
                SharedPreferences.Editor editor = sPref.edit();
                editor.putBoolean(Strings.stat_mode, true);
                editor.apply();
                wl.release();
            } else {
                refreshingFinished = true;
            }
            serviceRunning = false;
            stopSelf();
        }
    };

    private Runnable estimateDownload = new Runnable() {
        @Override
        public void run() {
            Log.d(LOG_TAG, "Dialogs size is " + Integer.toString(dialogs.size()));
            messageToCountOrLast(dialogs, VKWorker.GET_COUNT);
        }
    };

    private Runnable getLastMessageIds = new Runnable() {
        @Override
        public void run() {
            setQueueToUpdate();
            ArrayList<Integer> needToCollect = new ArrayList<>();
            for (Integer dialog : dialogs) {
                if (worker.dialogData.get(dialog).lastMessageId == -1)
                    needToCollect.add(dialog);
            }
            if (!needToCollect.isEmpty()) {
                messageToCountOrLast(needToCollect, VKWorker.GET_LAST);
            } else {
                Message m = dataHandler.obtainMessage();
                m.what = VKWorker.FINISH_GET_LAST;
                dataHandler.sendMessage(m);
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
        public int doWork(VKResponse response, int peer_id) {
            try {
                DialogData thisDialog = worker.dialogData.get(peer_id);
                DialogData globalData = worker.dialogData.get(DialogData.GLOBAL_DATA_ID, null);
                if (globalData == null) {
                    globalData = new DialogData(DialogData.GLOBAL_DATA_ID,
                            Easies.DIALOG_TYPE.CHAT);
                    worker.dialogData.put(DialogData.GLOBAL_DATA_ID, globalData);
                }
                BufferedWriter writer = dataManager.getWriter(peer_id);
                JSONObject res = response.json.getJSONObject("response");
                JSONArray messages = res.getJSONArray("result");
                int skipped = 0;

                DialogData update = new DialogData(thisDialog.dialog_id, thisDialog.type);

                boolean isChat = thisDialog.type == Easies.DIALOG_TYPE.CHAT;
                if (res.getJSONArray("result").getJSONObject(0).has("skipped")) {
                    skipped = res.getJSONArray("result").getJSONObject(0).getInt("skipped");
                }
                int id = -1;
                for (int i = 0; i < messages.length(); ++i) {
                    JSONArray msg = messages.getJSONObject(i).getJSONArray("items");
                    for (int j = msg.length() - 1; j >= 0; --j) {
                        JSONObject js = msg.getJSONObject(j);
                        int cur_id = js.getInt("id");
                        if (cur_id > id) {
                            update.messages++;
                            id = cur_id;
                            int from_id = js.getInt("from_id");
                            if (!worker.globalData.contains(from_id)) {
                                needToKnowUsernames.add(from_id);
                                Log.d(LOG_TAG, Integer.toString(from_id));
                                worker.globalData.putUser(new GlobalData.User(from_id, "Неизвестный", 2));
                            }
                            if (isChat) {
                                if (update.chatters.containsKey(from_id)) {
                                    int m = update.chatters.get(from_id);
                                    update.chatters.put(from_id, ++m);
                                } else {
                                    update.chatters.put(from_id, 1);
                                }
                            }
                            int cur_out = js.getInt("out");
                            int date = js.getInt("date");
                            currentDialogMessagesTime.add(date);
                            String body = js.getString("body");
                            boolean isOut = cur_out == 1;
                            if (isOut) {
                                update.out++;
                                update.out_symbols += body.length();
                            }
                            update.symbols += body.length();
                            if (js.has("attachments")) {
                                JSONArray attachments = js.getJSONArray("attachments");
                                for (int k = 0; k < attachments.length(); ++k) {
                                    JSONObject attachment = attachments.getJSONObject(k);
                                    switch (attachment.getString("type")) {
                                        case "video":
                                            if (isOut) update.videos++;
                                            else update.other_videos++;
                                            break;
                                        case "photo":
                                            if (isOut) update.pictures++;
                                            else update.other_pictures++;
                                            break;
                                        case "audio":
                                            if (isOut) update.audios++;
                                            else update.other_audios++;
                                            break;
                                        case "wall":
                                            if (isOut) update.walls++;
                                            else update.other_walls++;
                                            break;
                                        case "gift":
                                            if (isOut) update.gifts++;
                                            else update.other_gifts++;
                                            break;
                                        case "link":
                                            if (isOut) update.link_attachms++;
                                            else update.other_link_attachms++;
                                            break;
                                        case "doc":
                                            if (isOut) update.docs++;
                                            else update.other_docs++;
                                            break;
                                        case "sticker":
                                            if (isOut) update.stickers++;
                                            else update.other_stickers++;
                                            break;
                                        default:
                                            Log.d(LOG_TAG, "Unsupported attachment type: "
                                                    + attachment.getString("type"));

                                    }
                                }
                            }
                            try {
                                writer.newLine();
                            } catch (IOException ex) {
                                Log.wtf(LOG_TAG, ex.toString());
                            }
                        }
                    }
                }
                progress += update.messages;
                thisDialog.update(update);
                globalData.update(update);
                thisDialog.messages = Math.min(thisDialog.messages, worker.realMessages.get(thisDialog.dialog_id));
                JSONObject first = response.json.getJSONObject("response")
                        .getJSONArray("result")
                        .getJSONObject(0)
                        .getJSONArray("items")
                        .getJSONObject(0);
                String message_name = (first.getInt("out") == 1 ? "Вы" :
                        worker.globalData.getUser(first.getInt("from_id")).name);
                currentMessageData = new MessageData(peer_id, first.getString("body"), first.getInt("date"),
                        worker.dialogData.get(peer_id), ++messageId, message_name);
                Log.d(LOG_TAG, "Message: " + currentMessageData.message);
                Log.d(LOG_TAG, "Progress " + Integer.toString(progress));
                if (notifications) mNotifyManager.notify(NOTIFICATION_ID, mBuilder
                        .setProgress(worker.allMessages, progress, false)
                        .setContentText(getString(R.string.download_text))
                        .build());
                thisDialog.lastMessageId = id;
                if (skipped == 0) {
                    dataManager.saveEntries(currentDialogMessagesTime, peer_id, getApplicationContext());
                    dataManager.close(peer_id);
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

    private void sendMessageGetMessagePack(int peer_id, int new_start) {
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

    private Runnable getDialogsFromVK = new Runnable() {
        @Override
        public void run() {
            preparedThreadsReady = true;
            Message m = requestHandler.obtainMessage();
            m.what = VKWorker.GET_DIALOGS;
            m.arg1 = 200;
            m.arg2 = currentOffset;
            currentOffset += 200;
            requestHandler.sendMessage(m);
        }
    };

    private OneArg nextDialog = new OneArg() {
        @Override
        public void call(final int peer_id) {
            currentDialogMessagesTime = dataManager.getPrevEntries(peer_id, getApplicationContext());
            Log.d(LOG_TAG, "" + peer_id + ": " + worker.dialogData.get(peer_id).lastMessageId);
            sendMessageGetMessagePack(peer_id, worker.dialogData.get(peer_id).lastMessageId);
        }
    };

    private Runnable getDialogs = new Runnable() {
        @Override
        public void run() {
            dialogs = new ArrayList<>();
            ArrayList<DialogData> dialogDataSorted = new ArrayList<>();
            for (int i = 0; i < worker.dialogData.size(); ++i) {
                dialogDataSorted.add(worker.dialogData.valueAt(i));
            }
            Collections.sort(dialogDataSorted, new Comparator<DialogData>() {
                @Override
                public int compare(DialogData dialogData, DialogData t1) {
                    return t1.date - dialogData.date;
                }
            });
            for (DialogData d : dialogDataSorted) {
                dialogs.add(d.dialog_id);
            }
        }
    };

    public void debugDrop() {
        SparseArray<DialogData> t = new SparseArray<DialogData>();
        t.put(10, null);
        t.put(20, new DialogData(1, Easies.DIALOG_TYPE.CHAT));
        Easies.serializeData(t, getApplicationContext());
        SparseArray<DialogData> assertion = deserializeData(getApplicationContext());
        assert assertion.get(10) == null;
        assert assertion.get(20).type == Easies.DIALOG_TYPE.CHAT;
        String fileName = "dialogData.out";
        File dialogDataFile = new File(Easies.getSerializablePath(getApplicationContext()) + fileName);
        if (dialogDataFile.exists()) {
            if (dialogDataFile.delete()) {
                Log.d(LOG_TAG, "Successfully deleted prevData");
            } else {
                Log.d(LOG_TAG, "Previous data isn't exist");
            }
        }
    }

    private void sendNotification() {
        Intent notificationIntent = new Intent(this, LoadingActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle(getString(R.string.download_title))
                .setContentText(getString(R.string.download_text))
                .setSmallIcon(R.mipmap.service_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.service_icon))
                .setContentIntent(intent)
                .setOngoing(true);
    }

    private MessagesCollectorNew.ResponseWork whenGotDialogs = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id) {
            Log.d(LOG_TAG, "WHEN GOT DIALOGS");
            ArrayList<Integer> user_ids = new ArrayList<>(), group_ids = new ArrayList<>();
            try {
                Log.d(LOG_TAG, "when got dialogs");
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
                            String link = message.has("photo_100") ? message.getString("photo_100") : Strings.no_photo;
                            DialogData chatData = worker.prevDialogData.get(dialog_id, new DialogData(dialog_id, Easies.DIALOG_TYPE.CHAT));
                            chatData.link = link;
                            chatData.name = message.getString("title");
                            chatData.date = worker.time.get(dialog_id);
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
            VKWorker.PackRequest packRequest = new VKWorker.PackRequest(requestHandler);
            if (user_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_USERS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("user_ids", user_ids);
                m.setData(b);
                packRequest.addMessage(m);
            }
            if (group_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_GROUPS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("group_ids", group_ids);
                m.setData(b);
                packRequest.addMessage(m);
            }
            packRequest.finishAdding();
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotUsers = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id) {
            try {
                JSONArray users = response.json.getJSONArray("response");
                for (int i = 0; i < users.length(); ++i) {
                    JSONObject user = users.getJSONObject(i);
                    int dialog_id = user.getInt(Strings.id);
                    String username = user.getString("first_name") + " " + user.getString("last_name");
                    String link = user.has("photo_100") ? user.getString("photo_100") : Strings.no_photo;
                    DialogData userData = worker.prevDialogData.get(dialog_id, new DialogData(dialog_id, Easies.DIALOG_TYPE.USER));
                    userData.link = link;
                    userData.name = username;
                    userData.date = worker.time.get(dialog_id);
                    worker.dialogData.put(dialog_id, userData);

                    needToKnowUsernames.add(dialog_id);
                    worker.globalData.putUser(new GlobalData.User(dialog_id, username, 0));
                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotGroups = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id) {
            try {
                JSONArray array = response.json.getJSONArray("response");
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject jj = array.getJSONObject(i);
                    int id = -jj.getInt("id");
                    Log.d(LOG_TAG, "when got groups");
                    String link = jj.has("photo_100") ? jj.getString("photo_100") : Strings.no_photo;
                    DialogData groupData = worker.prevDialogData.get(id, new DialogData(id, Easies.DIALOG_TYPE.COMMUNITY));
                    groupData.link = link;
                    groupData.name = array.getJSONObject(i).getString("name");
                    groupData.date = worker.time.get(id);
                    worker.dialogData.put(id, groupData);
                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private Runnable dumperInitialization = new Runnable() {
        @Override
        public void run() {
            dumper.setOnFinishCount(getLastMessageIds);
            dumper.setOnFinishLast(collectMessages);
            dumper.setOnFinishMessages(getUsernames);
            dumper.setOnFinishGetUsernames(finishWork);
            dumper.setOnFinishGetDialogs(new Runnable() {
                @Override
                public void run() {
                    if (currentOffset < worker.dialogsCount) {
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
            dumper.setWhenGotUsernames(whenGotUsernames);
            dumper.setWhenConnectionLost(whenConnectionLost);
            dumper.setWhenConnectionRestored(whenConnectionRestored);
            dumper.setNextDialog(nextDialog);
            dumper.setProcess(dumpMessagePack);
            Log.d(LOG_TAG, "All shit is set");
        }
    };

    private ResponseWork whenGotUsernames = new ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id) {
            try {
                JSONArray users = response.json.getJSONArray("response");
                for (int i = 0; i < users.length(); ++i) {
                    JSONObject user = users.getJSONObject(i);
                    int dialog_id = user.getInt(Strings.id);
                    String username = user.getString("first_name") + " " + user.getString("last_name");
                    int sex = user.getInt("sex");
                    worker.globalData.putUser(new GlobalData.User(dialog_id, username, sex));
                }

            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    public Runnable whenConnectionLost = new Runnable() {
        @Override
        public void run() {
            if (runOnlyOnceWhenConnectionLost) {
                mBuilder.setOngoing(true)
                        .setContentText("Восстановление соединения...")
                        .setProgress(0, 0, true);
                if (notifications) mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                serializeData(worker.dialogData, getApplicationContext());
                runOnlyOnceWhenConnectionLost = false;
            }
            sendMessageToWorker(VKWorker.TRY_CONNECTION);
        }
    };

    private Runnable whenConnectionRestored = new Runnable() {
        @Override
        public void run() {
            runOnlyOnceWhenConnectionLost = true;
            sendMessageToWorker(VKWorker.CONTINUE_WORK);
        }
    };

    private Runnable getUsernames = new Runnable() {
        @Override
        public void run() {
            if (needToKnowUsernames.size() == 0) {
                Message m = dataHandler.obtainMessage();
                m.what = VKWorker.FINISH_GET_USERNAMES;
                dataHandler.sendMessage(m);
            } else {
                int i = 0;
                VKWorker.PackRequest packRequest = new VKWorker.PackRequest(requestHandler);
                do {
                    Log.d(LOG_TAG, "Send list " + Integer.toString(i));
                    Message m = requestHandler.obtainMessage();
                    m.what = VKWorker.GET_USERNAMES;
                    Bundle b = new Bundle();
                    b.putIntegerArrayList("user_ids",
                            new ArrayList<>(needToKnowUsernames.subList(i, Math.min(i + 700, needToKnowUsernames.size()))));
                    m.setData(b);
                    packRequest.addMessage(m);
                    i += 700;
                } while (i < needToKnowUsernames.size());
                packRequest.finishAdding();
            }
        }
    };

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void sendMessageToWorker(int flag) {
        Message m = requestHandler.obtainMessage();
        m.what = flag;
        requestHandler.sendMessage(m);
    }
}
