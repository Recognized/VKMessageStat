package com.vladsaif.vkmessagestat.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.util.Log;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.util.Calendar;
import java.util.Date;

public class MessagesCollector extends Service {
    private Handler mainHandler;
    private String access_token;
    private static final String LOG_TAG = "MyService";
    private final DbHelper dbHelper;
    private final SQLiteDatabase db;
    static int requests = 0;
    static final int fixedDelayMs = 1000;
    static long prev_request = 0;

    public MessagesCollector() {
        dbHelper = new DbHelper(getApplicationContext(), Strings.dialogs);
        db = dbHelper.getWritableDatabase();
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

    public final class DumpMessagePack implements Runnable {
        private int peer_id;
        private int start_message_id;

        public DumpMessagePack(int peer_id, int start_message_id) {
            db.execSQL("CREATE TABLE IF NOT EXISTS t" + Integer.toString(peer_id) +
                    " (message_id INTEGER PRIMARY KEY ON CONFLICT REPLACE, body TEXT, date INT)");
            this.peer_id = peer_id;
            this.start_message_id = start_message_id;
        }

        @Override
        public void run() {
            getMessages(start_message_id);
        }

        void getMessages(int start_message_id) {
            new CountedRequest("execute.getMessages", VKParameters.from(Strings.access_token, access_token,
                    Strings.peer_id, Integer.toString(peer_id), Strings.start_message_id,
                    Integer.toString(start_message_id)), new ResponseWork() {
                @Override
                public int doWork(VKResponse response) {

                }
            }, new DumpCallback() {
                @Override
                public void onComplete(int new_start_message_id) {
                    if (new_start_message_id < 0) ;
                    getMessages(new_start_message_id);
                }
            });
        }
    }

    public final class CountedRequest {

        public CountedRequest(final String method, final VKParameters param,
                              final ResponseWork work, final DumpCallback callback) {
            requests++;
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
                mainHandler.post(task);
            } else {
                mainHandler.postDelayed(task, delay);
            }
        }
    }

    public interface ResponseWork {
        int doWork(VKResponse response);
    }

    public interface DumpCallback {
        void onComplete(int new_start_message_id);
    }


}
