package com.vladsaif.vkmessagestat.services;

import android.content.Context;
import android.os.*;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.vk.sdk.api.VKError;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.db.GlobalData;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class VKWorker extends HandlerThread {
    private final String LOG_TAG = VKWorker.class.getSimpleName();
    private final long fixedDelay = 350;
    private final Handler callback;
    private Handler dumper;
    public Handler mHandler;
    public String access_token;
    public SparseIntArray realMessages;
    public SparseArray<DialogData> dialogData;
    public SparseArray<DialogData> prevDialogData;
    public GlobalData globalData;
    public SparseIntArray time;
    public int allMessages;
    public int dialogsCount;
    private ArrayDeque<VKRequest> failedRequests = new ArrayDeque<>();
    public static final int GET_LAST = 1;
    public static final int GET_COUNT = 2;
    public static final int GET_MESSAGES = 3;
    public static final int GET_DIALOGS = 4;
    public static final int GET_USERS = 5;
    public static final int GET_GROUPS = 6;
    public static final int GET_USERNAMES = 7;
    public static final int BEGIN_COLLECTING = 101;
    public static final int FINISH_GET_LAST = 1001;
    public static final int FINISH_GET_COUNT = 1002;
    public static final int FINISH_GET_MESSAGES = 1003;
    public static final int FINISH_GET_DIALOGS = 1004;
    public static final int FINISH_GET_USERS = 1005;
    public static final int FINISH_GET_GROUPS = 1006;
    public static final int FINISH_GET_USERNAMES = 1007;
    public static final int HTTP_ERROR = 666;
    public static final int TRY_CONNECTION = 10001;
    public static final int CONNECTION_RESTORED = 10002;
    public static final int CONTINUE_WORK = 10003;
    private static int id_generator = 0;

    public VKWorker(Handler dumper, String access_token, Handler callback, Context context) {
        super("VKWorker");
        this.dumper = dumper;
        this.access_token = access_token;
        realMessages = new SparseIntArray();
        time = new SparseIntArray();
        dialogData = new SparseArray<>();
        prevDialogData = Easies.deserializeData(context);
        dialogData = this.prevDialogData.clone();
        globalData = new GlobalData(context);
        allMessages = 0;
        this.callback = callback;
    }

    private ListenerWithError getDialogsListener = new ListenerWithError() {
        @Override
        public void onComplete(VKResponse response) {
            notifyWorkFinished(FINISH_GET_DIALOGS, response, -1);
        }
    };

    @Override
    public void run() {
        Looper.prepare();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                final Bundle b = msg.getData();
                final int id = b.getInt("id", -1);
                Log.d(LOG_TAG, Long.toString(new Date().getTime()));
                Log.d(LOG_TAG, Integer.toString(msg.what));
                switch (msg.what) {
                    case GET_COUNT:
                        VKRequest req = new VKRequest("execute.getCount",
                                peersToParam(b.getIntegerArrayList("peers")));
                        req.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                count(response);
                                notifyWorkFinished(FINISH_GET_COUNT, null, id);
                            }
                        };
                        req.start();
                        break;
                    case GET_LAST:
                        VKRequest last = new VKRequest("execute.getLast", peersToParam(b.getIntegerArrayList("peers")));
                        last.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                registerIds(response);
                                notifyWorkFinished(FINISH_GET_LAST, null, id);
                            }
                        };
                        last.start();
                        break;
                    case GET_MESSAGES:
                        VKRequest messages = new VKRequest("execute.getMessages",
                                VKParameters.from(Strings.access_token, access_token,
                                        Strings.peer_id, Integer.toString(b.getInt(Strings.peer_id)), Strings.start_message_id,
                                        Integer.toString(b.getInt(Strings.start_message_id))));
                        messages.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                Log.d(LOG_TAG, "my listener");
                                Message m = dumper.obtainMessage();
                                m.what = FINISH_GET_MESSAGES;
                                m.obj = response;
                                m.arg1 = b.getInt(Strings.peer_id);
                                m.arg2 = b.getInt(Strings.progress);
                                dumper.sendMessage(m);
                            }
                        };
                        messages.start();
                        break;
                    case GET_DIALOGS:
                        int count = msg.arg1;
                        int offset = msg.arg2;
                        VKRequest getDialogs = new VKRequest("messages.getDialogs",
                                VKParameters.from("count", Integer.toString(count), "offset", Integer.toString(offset),
                                        "v", "5.67"));
                        getDialogs.requestListener = getDialogsListener;
                        getDialogs.start();
                        break;
                    case GET_USERS:
                        VKRequest users = new VKRequest("users.get",
                                VKParameters.from("user_ids", Easies.join(b.getIntegerArrayList("user_ids")),
                                        "fields", "has_photo,photo_100", "v", "5.67"));
                        users.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                notifyWorkFinished(FINISH_GET_USERS, response, id);
                            }
                        };
                        users.start();
                        break;
                    case GET_GROUPS:
                        VKRequest groups = new VKRequest("groups.getById",
                                VKParameters.from("group_ids", Easies.join(b.getIntegerArrayList("group_ids")),
                                        "v", "5.67"));
                        groups.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                notifyWorkFinished(FINISH_GET_GROUPS, response, id);
                            }
                        };
                        groups.start();
                        break;
                    case GET_USERNAMES:
                        VKRequest usernames = new VKRequest("users.get",
                                VKParameters.from("user_ids", Easies.join(b.getIntegerArrayList("user_ids")),
                                        "fields", "has_photo,photo_100,sex", "v", "5.67"));
                        usernames.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                notifyWorkFinished(FINISH_GET_USERNAMES, response, id);
                            }
                        };
                        usernames.start();
                        break;
                    case TRY_CONNECTION:
                        VKRequest connection = new VKRequest("utils.getServerTime", VKParameters.from("v", "5.67"));
                        connection.requestListener = new ListenerWithError() {
                            @Override
                            public void onComplete(VKResponse response) {
                                Message m = dumper.obtainMessage();
                                m.what = CONNECTION_RESTORED;
                                mHandler.removeCallbacksAndMessages(null);
                                dumper.sendMessage(m);
                            }
                        };
                        connection.start();
                        break;
                    case CONTINUE_WORK:
                        VKRequest failedRequest = failedRequests.removeFirst();
                        Log.d(LOG_TAG, failedRequest.methodName);
                        failedRequest.attempts = 1;
                        failedRequest.start();
                        break;
                }
                synchronized (VKWorker.this) {
                    try {
                        VKWorker.this.wait(fixedDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        Message m = callback.obtainMessage();
        m.what = PreparedThreads.WORKER_STARTED;
        callback.sendMessage(m);
        Looper.loop();
    }

    public abstract class ListenerWithError extends VKRequest.VKRequestListener {

        @Override
        public void onError(VKError error) {
            if (error.errorCode == VKError.VK_REQUEST_HTTP_FAILED) {
                if (!error.request.methodName.equals("utils.getServerTime")) {
                    Log.d(LOG_TAG, error.request.methodName);
                    if (error.request.requestListener instanceof ListenerWithError)
                        Log.d(LOG_TAG, "listener with error");
                    VKRequest failed = new VKRequest(error.request.methodName, error.request.getMethodParameters());
                    failed.requestListener = error.request.requestListener;
                    failedRequests.addLast(failed);
                }
                Message m = dumper.obtainMessage();
                m.what = HTTP_ERROR;
                dumper.sendMessage(m);
            } else if (error.apiError != null && error.apiError.errorCode == 6) {
                synchronized (VKWorker.this) {
                    try {
                        VKWorker.this.wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                error.request.attempts = 1;
                error.request.start();
            } else throw new RuntimeException(error.request.toString() + error.toString());
        }

        @Override
        public abstract void onComplete(VKResponse response);
    }

    private void notifyWorkFinished(int what, Object obj, int id) {
        Message m = dumper.obtainMessage();
        m.what = what;
        m.obj = obj;
        Bundle b = new Bundle();
        b.putInt("id", id);
        m.setData(b);
        dumper.sendMessage(m);
    }

    private void registerIds(VKResponse response) {
        try {
            JSONArray array = response.json.getJSONArray("response");
            for (int i = 0; i < array.length(); ++i) {
                JSONObject obj = array.getJSONObject(i);
                dialogData.get(obj.getInt("peer_id")).lastMessageId = obj.getInt("id");
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
                allMessages += count - dialogData.get(peer).messages;
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

    public static class PackRequest {
        private static final String LOG_TAG = PackRequest.class.getSimpleName();
        private static SparseArray<PackRequest> allPacks = new SparseArray<>();
        private Set<Integer> id_set = new TreeSet<>();


        private ArrayList<Message> messages = new ArrayList<>();
        private Handler handler;

        public PackRequest(Handler handler) {
            this.handler = handler;
        }

        public static boolean requestFinished(Message m) {
            int id = m.getData().getInt("id", -1);
            PackRequest p = allPacks.get(id, null);
            if (p == null) {
                return true;
            } else {
                p.messageProceeded(id);
                return p.isAllMessagesProceeded();
            }
        }

        private void messageProceeded(int id) {
            id_set.remove(id);
        }

        private boolean isAllMessagesProceeded() {
            return id_set.isEmpty();
        }

        public void addMessage(Message m) {
            Bundle b = m.getData();
            int id = id_generator++;
            allPacks.put(id, this);
            this.id_set.add(id);
            b.putInt("id", id);
            m.setData(b);
            messages.add(m);
        }

        public void finishAdding() {
            for (Message m : messages) {
                handler.sendMessage(m);
            }
        }

    }
}
