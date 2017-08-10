package com.vladsaif.vkmessagestat.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DialogData;
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
import java.util.*;

import static com.vladsaif.vkmessagestat.utils.Easies.deserializeData;
import static com.vladsaif.vkmessagestat.utils.Easies.serializeData;

public class MessagesCollectorNew extends Service {
    private static final String LOG_TAG = "MyService";
    private final int NOTIFICATION_ID = 42;
    private final int packSize = 25 * 199;
    private String access_token;
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
    private Runnable handlersDereference = new Runnable() {
        @Override
        public void run() {
            worker = preparedThreads.worker;
            dumper = preparedThreads.dumper;
            requestHandler = preparedThreads.requestHandler;
            dataHandler = preparedThreads.dataHandler;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        access_token = VKAccessToken.currentToken().accessToken;
        dataManager = new DataManager(getApplicationContext());
        sendNotification();
        Log.d(LOG_TAG, "Pre-debug drop");
        debugDrop();
        Log.d(LOG_TAG, "Access token: " + access_token);
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

        public boolean getEstimatingState() {
            return estimating;
        }
    }

    public interface ResponseWork {
        int doWork(VKResponse response, int peer_id, long currentProgress);
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
                preparedThreads = new PreparedThreads(dumperInitialization, handlersDereference, getDialogsFromVK, getApplicationContext());
                progress = 0;
                break;
            // TODO
        }
        return START_NOT_STICKY;
    }

    private void messageToCountOrLast(ArrayList<Integer> src, int flag) {
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

    private void setQueueToUpdate() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Integer dialog : dialogs) {
            if (worker.realMessages.get(dialog) - worker.dialogData.get(dialog).messages == 0) break;
            queue.addLast(dialog);
        }
        dumper.setQueue(queue);
    }

    private Runnable finishWork = new Runnable() {
        @Override
        public void run() {
            serializeData(worker.dialogData, getApplicationContext());
            sendFinishNotification();
            SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
            SharedPreferences.Editor editor = sPref.edit();
            editor.putBoolean(Strings.stat_mode, true);
            editor.apply();
            stopSelf();
        }
    };

    private Runnable estimateDownload = new Runnable() {
        @Override
        public void run() {
            dumper.expect_count = dialogs.size() / 25 + (dialogs.size() % 25 == 0 ? 0 : 1);
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
        public int doWork(VKResponse response, int peer_id, long currentProgress) {
            try {
                 /*
                   Template for message data that will store in txt file
                   [message_id][from_id][out][body][date]
                 */
                DialogData thisDialog = worker.dialogData.get(peer_id);
                DialogData prevData = worker.prevDialogData.get(peer_id, null);
                long diff = worker.realMessages.get(peer_id) - (prevData == null ? 0 : prevData.messages);
                BufferedWriter writer = dataManager.getWriter(peer_id);
                JSONObject res = response.json.getJSONObject("response");
                JSONArray messages = res.getJSONArray("result");
                int messages_progress = 0;
                int videos = 0;
                int photos = 0;
                int symbols = 0;
                int walls = 0;
                int out = 0;
                int audios = 0;
                int skipped = 0;
                int out_symbols = 0;
                TreeMap<Integer, Integer> chatters = new TreeMap<>();
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
                            ++messages_progress;
                            id = cur_id;
                            int from_id = js.getInt("from_id");
                            if (isChat) {
                                if (chatters.containsKey(from_id)) {
                                    int m = chatters.get(from_id);
                                    chatters.put(from_id, ++m);
                                } else {
                                    chatters.put(from_id, 1);
                                }
                            }
                            int cur_out = js.getInt("out");
                            int date = js.getInt("date");
                            String body = js.getString("body");
                            if (cur_out == 1) {
                                out++;
                                out_symbols += body.length();
                            }
                            symbols += body.length();
                            putData(writer, Integer.toString(cur_id));
                            putData(writer, Integer.toString(from_id));
                            putData(writer, Integer.toString(cur_out));
                            putData(writer, body.replace('[', ' ').replace(']', ' '));
                            putData(writer, Integer.toString(date));
                            if (js.has("attachments")) {
                                JSONArray attachments = js.getJSONArray("attachments");
                                for (int k = 0; k < attachments.length(); ++k) {
                                    JSONObject attachment = attachments.getJSONObject(k);
                                    switch (attachment.getString("type")) {
                                        case "video":
                                            videos++;
                                            break;
                                        case "photo":
                                            photos++;
                                            break;
                                        case "audio":
                                            audios++;
                                            break;
                                        case "wall":
                                            walls++;
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
                progress += messages_progress;
                Log.d(LOG_TAG, String.format("Update: %d %d %d %d %d %d %d %d", progress, symbols, videos, photos, audios, walls, out, out_symbols));
                thisDialog.update(messages_progress, symbols, videos, photos, audios, walls, out, out_symbols, chatters);
                JSONObject first = response.json.getJSONObject("response")
                        .getJSONArray("result")
                        .getJSONObject(0)
                        .getJSONArray("items")
                        .getJSONObject(0);
                currentMessageData = new MessageData(peer_id, first.getString("body"), first.getInt("date"),
                        worker.dialogData.get(peer_id), ++messageId);
                Log.d(LOG_TAG, "Message: " + currentMessageData.message);
                Log.d(LOG_TAG, "Progress " + Integer.toString(progress));
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.setProgress(worker.allMessages, progress, false).build());
                if (skipped == 0) {
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(intent)
                .setOngoing(true);
    }

    private MessagesCollectorNew.ResponseWork whenGotDialogs = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, long currentProgress) {
            Log.d(LOG_TAG, "WHEN GOT DIALOGS");
            ArrayList<Integer> user_ids = new ArrayList<>(), group_ids = new ArrayList<>();
            try {
                Log.d(LOG_TAG, "Dialogs collecting");
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
            if (user_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_USERS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("user_ids", user_ids);
                m.setData(b);
                requestHandler.sendMessage(m);
            } else {
                dumper.gotUsers = true;
            }
            if (group_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_GROUPS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("group_ids", group_ids);
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
        public int doWork(VKResponse response, int peer_id, long currentProgress) {
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
                }

            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotGroups = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, long currentProgress) {
            try {
                JSONArray array = response.json.getJSONObject("response").getJSONArray("items");
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject jj = array.getJSONObject(i).getJSONObject("message");
                    int id = -jj.getInt("id");
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
            dumper.setOnFinishMessages(finishWork);
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
            dumper.setNextDialog(nextDialog);
            dumper.setProcess(dumpMessagePack);
            Log.d(LOG_TAG, "All shit is set");
        }
    };

    private void putData(BufferedWriter writer, String data) {
        try {
            writer.write((int) '[');
            writer.write(data);
            writer.write((int) ']');
        } catch (IOException ex) {
            Log.wtf(LOG_TAG, ex.toString());
        }
    }
}
