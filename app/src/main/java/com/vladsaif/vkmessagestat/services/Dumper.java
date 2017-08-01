package com.vladsaif.vkmessagestat.services;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;


public class Dumper extends Thread {
    public final int GET_DIALOGS = 4;
    public Handler dumper;
    public int expect_count;
    public int expect_last;
    public int expect_messages;
    private int current_count;
    private int current_last;
    private int current_messages;

    public void setProcess(MessagesCollectorNew.ResponseWork process) {
        this.process = process;
    }

    public void setNextDialog(MessagesCollectorNew.OneArg nextDialog) {
        this.nextDialog = nextDialog;
    }

    private MessagesCollectorNew.ResponseWork process;
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

    private ArrayDeque<Integer> queue;

    public void setQueue(ArrayDeque<Integer> queue) {
        this.queue = queue;
    }

    public Dumper() {
        current_count = 0;
        current_last = 0;
        current_messages = 0;
    }

    @Override
    public void run() {
        Looper.prepare();

        dumper = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VKWorker.FINISH_GET_COUNT:
                        current_count++;
                        if(current_count == expect_count) {
                            onFinishCount.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_LAST:
                        current_last++;
                        if(current_count == expect_count) {
                            onFinishLast.run();
                        }
                        break;
                    case VKWorker.FINISH_GET_MESSAGES:
                        if(process.doWork((VKResponse)msg.obj, msg.arg1, msg.arg2) < 0) {
                            if(!queue.isEmpty()) {
                                nextDialog.call(queue.getFirst());
                            } else {
                                onFinishMessages.run();
                            }
                        }
                        break;
                }
            }
        };

        Looper.loop();
    }
}
