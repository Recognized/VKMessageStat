package com.vladsaif.vkmessagestat.services;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import com.vk.sdk.VKAccessToken;
import com.vladsaif.vkmessagestat.db.Themes;

class PreparedThreads {
    public VKWorker worker;
    public Dumper dumper;
    public static final int DUMPER_STARTED = 50;
    public static final int WORKER_STARTED = 51;
    private final Handler helperHandler;
    public Handler dataHandler;
    public Handler requestHandler;

    public PreparedThreads(final Runnable dumperInitialization, final Runnable dereference, final Runnable callback, final Context context) {
        final HandlerThread helperThread = new HandlerThread("helper");
        helperThread.start();
        final String access_token = VKAccessToken.currentToken().accessToken;
        helperHandler = new Handler(helperThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case DUMPER_STARTED:
                        worker = new VKWorker(dumper.dumper, access_token, this, context);
                        worker.start();
                        break;
                    case WORKER_STARTED:
                        requestHandler = worker.mHandler;
                        dataHandler = dumper.dumper;
                        dataHandler.post(dereference);
                        dataHandler.post(dumperInitialization);
                        dataHandler.post(callback);
                        break;
                }
            }
        };
        helperHandler.post(new Runnable() {
            @Override
            public void run() {
                dumper = new Dumper(helperHandler);
                dumper.start();
                Themes.initialize(context);
            }
        });
    }
}
