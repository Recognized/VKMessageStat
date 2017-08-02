package com.vladsaif.vkmessagestat.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.services.Dumper;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.services.VKWorker;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainPage extends AppCompatActivity {

    private final String LOG_TAG = "myTag";
    public int responses;
    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private ProgressBar mProgress;
    private DbHelper dbHelper;
    private Button goAdvanced;
    public VKWorker vkWorker;
    public Dumper dumper;
    public Handler requestHandler;
    public Handler dataHandler;
    private String access_token;
    private SQLiteDatabase db;
    public int dialogsCount;


    @Override
    protected void onStart() {
        super.onStart();
        // Check if app can write database to sdcard or something
        // I don't really know how to check how much memory is available
        // so there is no other way for me except forgetting about this problem
        responses = 0;
        SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
        if (!sPref.contains(Strings.external_storage)) {
            SharedPreferences.Editor edit = sPref.edit();
            try {
                File test = new File(getExternalFilesDir(null), "test");
                OutputStream os = new FileOutputStream(test);
                Log.d(LOG_TAG, "using external storage");
                edit.putBoolean(Strings.external_storage, true);
            } catch (IOException ex) {
                Log.d(LOG_TAG, "using internal storage");
                edit.putBoolean(Strings.external_storage, false);
            }
            edit.apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        mProgress = (ProgressBar) findViewById(R.id.loadingRecycler);
        dbHelper = new DbHelper(getApplicationContext(), "dialogs.db");
        db = dbHelper.getWritableDatabase();
        dbHelper.onCreate(db);
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        access_token = VKAccessToken.currentToken().accessToken;
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        // TODO correct settings
        // setting common things
        SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
        STAT_MODE stat_mode = STAT_MODE.values()[(sPref.getInt(Strings.stat_mode, 0))];
        switch (stat_mode) {
            case SIMPLE:
                toolbar.setTitle(R.string.simple_stat);
                break;
            case ADVANCED:
                toolbar.setTitle(R.string.advanced_stat);
        }
        setSupportActionBar(toolbar);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        goAdvanced = (Button) findViewById(R.id.button);
        goAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MessagesCollectorNew.class);
                intent.putExtra(Strings.commandType, Strings.commandDump);
                startService(intent);
                Intent openProgress = new Intent(getApplicationContext(), LoadingActivity.class);
                startActivity(openProgress);
            }
        });
        (new PrepareThreads()).execute();
    }

    public final class PrepareThreads extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            dumper = new Dumper();
            dumper.start();
            while (dumper.dumper == null) ;
            vkWorker = new VKWorker(dumper.dumper, access_token);
            vkWorker.start();
            while (vkWorker.mHandler == null) ;
            requestHandler = vkWorker.mHandler;
            dataHandler = dumper.dumper;
            dumper.setWhenGotDialogs(whenGotDialogs);
            dumper.setWhenGotGroups(whenGotGroups);
            dumper.setWhenGotUsers(whenGotUsers);
            dumper.setOnFinishGetDialogs(onFinishGetDialogs);
            Log.d(LOG_TAG, "Threads are prepared");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Message m = requestHandler.obtainMessage();
            m.what = VKWorker.GET_DIALOGS;
            m.arg1 = 20;
            m.arg2 = 0;
            requestHandler.sendMessage(m);
            Log.d(LOG_TAG, "Requests sent");
        }
    }

    public MessagesCollectorNew.ResponseWork whenGotDialogs = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            ArrayList<Integer> user_ids = new ArrayList<>(), group_ids = new ArrayList<>();
            try {
                Log.d(LOG_TAG, "DIalogs collecting");
                JSONArray items = response.json.getJSONObject("response").getJSONArray("items");
                dialogsCount = response.json.getJSONObject("response").getInt("count");
                for (int i = 0; i < items.length(); ++i) {
                    JSONObject message = items.getJSONObject(i).getJSONObject("message");
                    int chat_id = message.has("chat_id") ? message.getInt("chat_id") : -1;
                    int user_id = message.getInt("user_id");
                    Easies.DIALOG_TYPE type = Easies.resolveTypeBySomeShitThankYouVK(user_id, chat_id);
                    int dialog_id = Easies.getDialogID(type, user_id, chat_id);
                    vkWorker.time.put(dialog_id, message.getInt("date"));
                    switch (type) {
                        case CHAT:
                            ContentValues val = new ContentValues();
                            val.put(Strings.dialog_id, dialog_id);
                            val.put(Strings.type, Strings.chat);
                            val.put(Strings.date, vkWorker.time.get(dialog_id));
                            db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                            ContentValues cv = new ContentValues();
                            cv.put(Strings.dialog_id, dialog_id);
                            cv.put(Strings.name, message.getString("title"));
                            db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            ContentValues con = new ContentValues();
                            con.put(Strings.dialog_id, dialog_id);
                            con.put(Strings.link, (message.has("photo_200") ? message.getString("photo_200") : Strings.no_photo));
                            db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);
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
            if(user_ids.size() > 0)
            {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_USERS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("user_ids", user_ids);
                m.setData(b);
                requestHandler.sendMessage(m);
            } else {
                dumper.gotUsers = true;
            }
            if(group_ids.size() > 0) {
                Message m = requestHandler.obtainMessage();
                m.what = VKWorker.GET_GROUPS;
                Bundle b = new Bundle();
                b.putIntegerArrayList("group_ids", user_ids);
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
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            try {
                JSONArray users = response.json.getJSONArray("response");
                for (int i = 0; i < users.length(); ++i) {
                    ContentValues val = new ContentValues();
                    JSONObject user = users.getJSONObject(i);
                    int dialog_id = user.getInt(Strings.id);
                    val.put(Strings.dialog_id, dialog_id);
                    val.put(Strings.type, Strings.user);
                    val.put(Strings.date, vkWorker.time.get(dialog_id));
                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues cv = new ContentValues();
                    cv.put(Strings.dialog_id, dialog_id);
                    cv.put(Strings.name, user.getString("first_name") + " " + user.getString("last_name"));
                    db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues con = new ContentValues();
                    con.put(Strings.dialog_id, dialog_id);
                    con.put(Strings.link, (user.has("photo_200") ? user.getString("photo_200") : Strings.no_photo));
                    db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);
                }

            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private MessagesCollectorNew.ResponseWork whenGotGroups = new MessagesCollectorNew.ResponseWork() {
        @Override
        public int doWork(VKResponse response, int peer_id, int currentProgress) {
            try {
                JSONArray array = response.json.getJSONObject("response").getJSONArray("items");
                for (int i = 0; i < array.length(); ++i) {
                    ContentValues val = new ContentValues();
                    JSONObject jj = array.getJSONObject(i).getJSONObject("message");
                    int id = -jj.getInt("id");
                    val.put(Strings.dialog_id, id);
                    val.put(Strings.type, Strings.community);
                    val.put(Strings.date, vkWorker.time.get(id));
                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues cv = new ContentValues();
                    cv.put(Strings.dialog_id, id);
                    cv.put(Strings.name, array.getJSONObject(i).getString("name"));
                    db.insertWithOnConflict(Strings.names, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    ContentValues con = new ContentValues();
                    con.put(Strings.dialog_id, id);
                    con.put(Strings.link, (jj.has("photo_200") ? jj.getString("photo_200") : Strings.no_photo));
                    db.insertWithOnConflict(Strings.pictures, null, con, SQLiteDatabase.CONFLICT_REPLACE);
                }
            } catch (JSONException ex) {
                Log.e(LOG_TAG, ex.toString());
            }
            return 0;
        }
    };

    private Runnable onFinishGetDialogs = new Runnable() {
        @Override
        public void run() {
            mProgress.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAdapter(new DialogsAdapter(dbHelper, MainPage.this, mLayoutManager));
            mRecyclerView.setOnScrollListener(((DialogsAdapter)mRecyclerView.getAdapter()).scrolling);
        }
    };

    enum STAT_MODE {
        SIMPLE, ADVANCED
    }
}



