package com.vladsaif.vkmessagestat.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.util.Calendar;
import java.util.Date;

public class MessagesCollector extends Service {
    private Handler mainHandler;
    private static String access_token;
    private final String LOG_TAG = "MyService";

    public MessagesCollector() {
    }


    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("MessagesCollector", HandlerThread.NORM_PRIORITY - 1);
        thread.start();
        mainHandler = new Handler(thread.getLooper());
        access_token = VKAccessToken.currentToken().accessToken;
        Log.d(LOG_TAG, access_token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static final class DumpMessagePack implements Runnable {
        private String peer_id;
        private String start_message_id;

        public DumpMessagePack() {

        }

        @Override
        public void run() {
            new CountedRequest("execute.getMessages", VKParameters.from(Strings.access_token, access_token,
                    Strings.peer_id, peer_id, Strings.start_message_id, start_message_id), new ResponseWork() {
                @Override
                public void doWork(VKResponse response) {

                }
            });
        }
    }

    public static final class CountedRequest {
        static int requests = 0;
        static final int fixedDelayMs = 1000;
        static long prev_request = 0;

        public CountedRequest(final String method, final VKParameters param, final ResponseWork work, final DumpCallback callback) {
            requests++;
            Handler handler = new Handler();
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
                handler.post(task);
            } else {
                handler.postDelayed(task, delay);
            }
        }
    }

    public interface ResponseWork {
        /* TYPE_T */ doWork(VKResponse response);
    }

    public interface DumpCallback {
        void onComplete(/* TYPE_T */);
    }


}
