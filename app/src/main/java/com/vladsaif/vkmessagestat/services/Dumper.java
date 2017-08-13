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
    public int expect_count;
    public int expect_last;
    private int current_count;
    private int current_last;
    public boolean gotDialogs;
    public boolean gotUsers;
    public boolean gotGroups;


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
    private Runnable onFinishGetDialogs;
    private Runnable whenConnectionRestored;
    private Runnable whenConnectionLost;

    private ArrayDeque<Integer> queue;

    public void setQueue(ArrayDeque<Integer> queue) {
        this.queue = queue;
    }

    public Dumper(Handler callback) {
        super("Dumper");
        current_count = 0;
        current_last = 0;
        gotUsers = false;
        gotGroups = false;
        gotDialogs = false;
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
                        Log.d(LOG_TAG, Integer.toString(current_count));
                        current_count++;
                        if (current_count == expect_count) {
                            Log.d(LOG_TAG, "FINISHED COUNT");
                            onFinishCount.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_LAST:
                        current_last++;
                        if (current_last == expect_last) {
                            onFinishLast.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_MESSAGES:
                        if (process.doWork((VKResponse) msg.obj, msg.arg1, msg.arg2) < 0) {
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
                        whenGotDialogs.doWork((VKResponse) msg.obj, 0, 0);
                        gotDialogs = true;
                        if (gotUsers && gotGroups) {
                            resetDialogsFlags();
                            uiHandler.post(onFinishGetDialogs);
                        }
                        break;
                    case VKWorker.FINISH_GET_USERS:
                        whenGotUsers.doWork((VKResponse) msg.obj, 0, 0);
                        gotUsers = true;
                        if (gotDialogs && gotGroups) {
                            resetDialogsFlags();
                            uiHandler.post(onFinishGetDialogs);
                        }
                        break;
                    case VKWorker.FINISH_GET_GROUPS:
                        whenGotGroups.doWork((VKResponse) msg.obj, 0, 0);
                        gotGroups = true;
                        if (gotDialogs && gotUsers) {
                            resetDialogsFlags();
                            uiHandler.post(onFinishGetDialogs);
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

    private void resetDialogsFlags() {
        gotDialogs = false;
        gotGroups = false;
        gotUsers = false;
    }

    public void setWhenConnectionRestored(Runnable whenConnectionRestored) {
        this.whenConnectionRestored = whenConnectionRestored;
    }

    public void setWhenConnectionLost(Runnable whenConnectionLost) {
        this.whenConnectionLost = whenConnectionLost;
    }
}
