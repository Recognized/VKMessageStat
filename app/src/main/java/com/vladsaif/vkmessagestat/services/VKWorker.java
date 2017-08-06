package com.vladsaif.vkmessagestat.services;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;

// Singleton
public class VKWorker extends Thread {
    private final String LOG_TAG = VKWorker.class.getSimpleName();
    private final long fixedDelay = 334;
    private Handler dumper;
    public Handler mHandler;
    public String access_token;
    public SparseIntArray existingMessages;
    public SparseIntArray realMessages;
    public SparseIntArray lastMessageIds;
    public SparseArray<DialogData> dialogData;
    public SparseIntArray time;
    public int allMessages;
    public int dialogsCount;
    public static final int GET_LAST = 1;
    public static final int GET_COUNT = 2;
    public static final int GET_MESSAGES = 3;
    public static final int GET_DIALOGS = 4;
    public static final int GET_USERS = 5;
    public static final int GET_GROUPS = 6;
    public static final int BEGIN_COLLECTING = 101;
    public static final int FINISH_GET_LAST = 1001;
    public static final int FINISH_GET_COUNT  = 1002;
    public static final int FINISH_GET_MESSAGES = 1003;
    public static final int FINISH_GET_DIALOGS = 1004;
    public static final int FINISH_GET_USERS = 1005;
    public static final int FINISH_GET_GROUPS = 1006;

    public VKWorker(Handler dumper, String access_token) {
        this.dumper = dumper;
        this.access_token = access_token;
        existingMessages = new SparseIntArray();
        realMessages = new SparseIntArray();
        lastMessageIds = new SparseIntArray();
        time = new SparseIntArray();
        dialogData = new SparseArray<>();
        allMessages = 0;
    }

    private ListenerWithError getCountListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            count(response);
            notifyWorkFinished(FINISH_GET_COUNT, null);
        }
    };

    private ListenerWithError getLastListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            registerIds(response);
            notifyWorkFinished(FINISH_GET_LAST, null);
        }
    };

    private ListenerWithError getDialogsListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            notifyWorkFinished(FINISH_GET_DIALOGS, response);
        }
    };

    private ListenerWithError getUsersListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            notifyWorkFinished(FINISH_GET_USERS, response);
        }
    };

    private ListenerWithError getGroupsListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            notifyWorkFinished(FINISH_GET_GROUPS, response);
        }
    };

    @Override
    public void run() {
        Looper.prepare();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                final Bundle b = msg.getData();
                Log.d(LOG_TAG, Long.toString(new Date().getTime()));
                Log.d(LOG_TAG, Integer.toString(msg.what));
                boolean secureMethod = true;
                switch (msg.what) {
                    case GET_COUNT:
                        VKRequest req = new VKRequest("execute.getCount",
                                peersToParam(b.getIntegerArrayList("peers")));
                        req.executeWithListener(getCountListener);
                        break;
                    case GET_LAST:
                        VKRequest last = new VKRequest("execute.getLast", peersToParam(b.getIntegerArrayList("peers")));
                        last.executeWithListener(getLastListener);
                        break;
                    case GET_MESSAGES:
                        VKRequest messages = new VKRequest("execute.getMessages",
                                VKParameters.from(Strings.access_token, access_token,
                                        Strings.peer_id, Integer.toString(b.getInt(Strings.peer_id)), Strings.start_message_id,
                                        Integer.toString(b.getInt(Strings.start_message_id))));
                        messages.executeWithListener(new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                Message m = dumper.obtainMessage();
                                m.what = FINISH_GET_MESSAGES;
                                m.obj = response;
                                m.arg1 = b.getInt(Strings.peer_id);
                                m.arg2 = b.getInt(Strings.progress);
                                dumper.sendMessage(m);
                            }
                        });
                        break;
                    case GET_DIALOGS:
                        int count = msg.arg1;
                        int offset = msg.arg2;
                        VKRequest getDialogs = new VKRequest("messages.getDialogs",
                                VKParameters.from("count", Integer.toString(count), "offset", Integer.toString(offset)));
                        getDialogs.executeWithListener(getDialogsListener);
                        break;
                    case GET_USERS:
                        VKRequest users = new VKRequest("users.get",
                                VKParameters.from("user_ids", Easies.join(b.getIntegerArrayList("user_ids")),
                                "fields", "has_photo,photo_100"));
                        users.executeWithListener(getUsersListener);
                        secureMethod = false;
                        break;
                    case GET_GROUPS:
                        VKRequest groups = new VKRequest("groups.getById",
                                VKParameters.from("group_ids", Easies.join(b.getIntegerArrayList("group_ids"))));
                        groups.executeWithListener(getGroupsListener);
                        secureMethod = false;
                        break;
                }
                if(secureMethod) {
                    synchronized (VKWorker.this) {
                        try {
                            VKWorker.this.wait(fixedDelay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        };

        Looper.loop();
    }

    public abstract class ListenerWithError extends VKRequest.VKRequestListener {

        @Override
        public void onError(VKError error) {
            throw new RuntimeException(error.toString());
        }

        @Override
        public abstract void onComplete(VKResponse response);
    }

    private void notifyWorkFinished(int what, Object obj) {
        Message m = dumper.obtainMessage();
        m.what = what;
        m.obj = obj;
        dumper.sendMessage(m);
    }

    private void registerIds(VKResponse response) {
        try {
            JSONArray array = response.json.getJSONArray("response");
            for (int i = 0; i < array.length(); ++i) {
                JSONObject obj = array.getJSONObject(i);
                lastMessageIds.put(obj.getInt("peer_id"), obj.getInt("id"));
            }
        } catch (JSONException ex) {
            throw new RuntimeException("Not expected. " + ex.toString());
        }
    }

    private void count(VKResponse response) {
        try {
            JSONArray array = response.json.getJSONArray("response");
            for (int i = 0; i < array.length(); ++i) {
                JSONObject obj = array.getJSONObject(i);
                int peer = obj.getInt("peer_id");
                int count = obj.getInt("count");
                realMessages.put(peer, count);
                allMessages += count - existingMessages.get(peer);
            }
        } catch (JSONException ex) {
            throw new RuntimeException("Not expected. " + ex.toString());
        }
    }

    private VKParameters peersToParam(ArrayList<Integer> peers) {
        // Achtung!! Extreme chance of making mistake, but it is Java and I can't do it better;
        String[] filteredPeers = new String[25];
        for (int i = 0; i < peers.size(); ++i) {
            filteredPeers[i] = Integer.toString(peers.get(i));
        }
        for (int i = peers.size(); i < 25; ++i) {
            filteredPeers[i] = "-1";
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
}
