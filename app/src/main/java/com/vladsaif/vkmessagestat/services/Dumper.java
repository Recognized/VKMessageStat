package com.vladsaif.vkmessagestat.services;


import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.vk.sdk.api.VKResponse;

import java.util.ArrayDeque;
import java.util.Date;


public class Dumper extends HandlerThread {
    public final String LOG_TAG = Dumper.class.getSimpleName();
    private final Handler callback;
    public Handler dumper;
    private Handler uiHandler;


    public void setProcess(MessagesCollectorNew.ResponseWork process) {
        this.process = process;
    }

    public void setNextDialog(MessagesCollectorNew.OneArg nextDialog) {
        this.nextDialog = nextDialog;
    }

    private MessagesCollectorNew.ResponseWork process;

    public void setWhenGotDialogs(MessagesCollectorNew.ResponseWork whenGotDialogs) {
        this.whenGotDialogs = whenGotDialogs;
    }

    public void setWhenGotUsers(MessagesCollectorNew.ResponseWork whenGotUsers) {
        this.whenGotUsers = whenGotUsers;
    }

    public void setWhenGotGroups(MessagesCollectorNew.ResponseWork whenGotGroups) {
        this.whenGotGroups = whenGotGroups;
    }

    public void setOnFinishGetDialogs(Runnable onFinishGetDialogs) {
        this.onFinishGetDialogs = onFinishGetDialogs;
    }

    private MessagesCollectorNew.ResponseWork whenGotDialogs;
    private MessagesCollectorNew.ResponseWork whenGotUsers;
    private MessagesCollectorNew.ResponseWork whenGotGroups;
    private MessagesCollectorNew.ResponseWork whenGotUsernames;
    private MessagesCollectorNew.OneArg nextDialog;

    public void setOnFinishLast(Runnable onFinishLast) {
        this.onFinishLast = onFinishLast;
    }

    public void setOnFinishMessages(Runnable onFinishMessages) {
        this.onFinishMessages = onFinishMessages;
    }

    public void setOnFinishCount(Runnable r) {
        onFinishCount = r;
    }

    private Runnable onFinishLast;
    private Runnable onFinishCount;
    private Runnable onFinishMessages;
    private Runnable onFinishGetUsernames;
    private Runnable onFinishGetDialogs;
    private Runnable whenConnectionRestored;
    private Runnable whenConnectionLost;

    private ArrayDeque<Integer> queue;

    public void setQueue(ArrayDeque<Integer> queue) {
        this.queue = queue;
    }

    public Dumper(Handler callback) {
        super("Dumper");
        uiHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
    }

    @Override
    public void run() {
        Looper.prepare();

        dumper = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.d(LOG_TAG, Long.toString(new Date().getTime()));
                Log.d(LOG_TAG, Integer.toString(msg.what));
                switch (msg.what) {
                    case VKWorker.FINISH_GET_COUNT:
                        if (VKWorker.PackRequest.requestFinished(msg)) {
                            Log.d(LOG_TAG, "FINISHED COUNT");
                            onFinishCount.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_LAST:
                        if (VKWorker.PackRequest.requestFinished(msg)) {
                            onFinishLast.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_MESSAGES:
                        if (process.doWork((VKResponse) msg.obj, msg.arg1) < 0) {
                            if (!queue.isEmpty()) {
                                nextDialog.call(queue.removeFirst());
                            } else {
                                Log.d(LOG_TAG, "Queue is empty");
                                onFinishMessages.run();
                            }
                        }
                        break;
                    case VKWorker.BEGIN_COLLECTING:
                        if (!queue.isEmpty()) {
                            nextDialog.call(queue.removeFirst());
                        } else {
                            Log.d(LOG_TAG, "begin collecting");
                            onFinishMessages.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_DIALOGS:
                        whenGotDialogs.doWork((VKResponse) msg.obj, 0);
                        break;
                    case VKWorker.FINISH_GET_USERS:
                        if (msg.obj != null) whenGotUsers.doWork((VKResponse) msg.obj, 0);
                        if (VKWorker.PackRequest.requestFinished(msg)) {
                            uiHandler.post(onFinishGetDialogs);
                        }
                        break;
                    case VKWorker.FINISH_GET_GROUPS:
                        if (msg.obj != null) whenGotGroups.doWork((VKResponse) msg.obj, 0);
                        if (VKWorker.PackRequest.requestFinished(msg)) {
                            uiHandler.post(onFinishGetDialogs);
                        }
                        break;
                    case VKWorker.FINISH_GET_USERNAMES:
                        if (msg.obj != null) whenGotUsernames.doWork((VKResponse) msg.obj, 0);
                        if (VKWorker.PackRequest.requestFinished(msg)) {
                            onFinishGetUsernames.run();
                        }
                        break;
                    case VKWorker.CONNECTION_RESTORED:
                        whenConnectionRestored.run();
                        break;
                    case VKWorker.HTTP_ERROR:
                        whenConnectionLost.run();
                        break;

                }
            }
        };
        Message m = callback.obtainMessage();
        m.what = PreparedThreads.DUMPER_STARTED;
        callback.sendMessage(m);
        Log.d(LOG_TAG, "Dumper inner before loop");
        Looper.loop();
    }

    public void setWhenConnectionRestored(Runnable whenConnectionRestored) {
        this.whenConnectionRestored = whenConnectionRestored;
    }

    public void setWhenConnectionLost(Runnable whenConnectionLost) {
        this.whenConnectionLost = whenConnectionLost;
    }

    public void setOnFinishGetUsernames(Runnable onFinishGetUsernames) {
        this.onFinishGetUsernames = onFinishGetUsernames;
    }

    public void setWhenGotUsernames(MessagesCollectorNew.ResponseWork getWhenGotUsernames) {
        this.whenGotUsernames = getWhenGotUsernames;
    }
}
