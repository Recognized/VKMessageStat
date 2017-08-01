package com.vladsaif.vkmessagestat.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.util.SparseIntArray;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class DbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String LOG_TAG = "DbHelper";
    public final SQLiteDatabase db;

    public DbHelper(final Context context, String databaseName) {
        super(new DatabaseContext(context), databaseName, null, DATABASE_VERSION);
        db = getWritableDatabase();
        onCreate(db);
    }

    public static void getDialogs(final SQLiteDatabase db, final MainPage context, int count) {
        // TODO maybe I need fix locale somehow
        VKRequest req = new VKRequest("messages.getDialogs", VKParameters.from("count", Integer.toString(count)));
        context.responses++;
        req.executeWithListener(new VKRequest.VKRequestListener() {
            @Override
            public void onComplete(VKResponse response) {
                super.onComplete(response);
                VKRequest users, groups;
                final ArrayList<Integer> user_ids = new ArrayList<>(), group_ids = new ArrayList<>();
                final SparseIntArray time = new SparseIntArray();
                try {
                    Log.d(LOG_TAG, db.getPath());
                    Log.d(LOG_TAG, response.responseString);
                    JSONArray items = response.json.getJSONObject("response").getJSONArray("items");
                    context.dialogsCount = response.json.getJSONObject("response").getInt("count");
                    for (int i = 0; i < items.length(); ++i) {
                        JSONObject message = items.getJSONObject(i).getJSONObject("message");
                        int chat_id = message.has("chat_id") ? message.getInt("chat_id") : -1;
                        int user_id = message.getInt("user_id");
                        Log.d(LOG_TAG, Integer.toString(user_id));
                        Easies.DIALOG_TYPE type = Easies.resolveTypeBySomeShitThankYouVK(user_id, chat_id);
                        int dialog_id = Easies.getDialogID(type, user_id, chat_id);
                        time.put(dialog_id, message.getInt("date"));
                        switch (type) {
                            case CHAT:
                                ContentValues val = new ContentValues();
                                val.put(Strings.dialog_id, dialog_id);
                                val.put(Strings.type, Strings.chat);
                                val.put(Strings.date, time.get(dialog_id));
                                db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                                db.execSQL("INSERT OR REPLACE INTO names VALUES (" + Integer.toString(dialog_id) + ", " +
                                        "'" + message.getString("title") + "');");
                                db.execSQL("INSERT OR REPLACE INTO pictures VALUES (" + Integer.toString(dialog_id) + ", " +
                                        "'" + (message.has("photo_200") ? message.getString("photo_200") : Strings.no_photo) + "');");
                                break;
                            case USER:
                                user_ids.add(dialog_id);
                                break;
                            case COMMUNITY:
                                group_ids.add(-dialog_id);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (user_ids.size() > 0) {
                    context.responses++;
                    users = new VKRequest("users.get", VKParameters.from("user_ids", Easies.join(user_ids),
                            "fields", "has_photo,photo_200"));
                    users.executeWithListener(new VKRequest.VKRequestListener() {
                        @Override
                        public void onComplete(VKResponse response) {
                            super.onComplete(response);
                            Log.d(LOG_TAG, db.getPath());
                            try {
                                JSONArray users = response.json.getJSONArray("response");
                                for (int i = 0; i < users.length(); ++i) {
                                    ContentValues val = new ContentValues();
                                    JSONObject user = users.getJSONObject(i);
                                    val.put(Strings.dialog_id, user.getInt(Strings.id));
                                    val.put(Strings.type, Strings.user);
                                    val.put(Strings.date, time.get(user.getInt(Strings.id)));
                                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                                    db.execSQL("INSERT OR REPLACE INTO names VALUES (" + Integer.toString(user.getInt("id")) + ", " +
                                            "'" + user.getString("first_name") + " " + user.getString("last_name") + "');");
                                    db.execSQL("INSERT OR REPLACE INTO pictures VALUES (" + Integer.toString(user.getInt("id")) + ", " +
                                                "'" + (user.has("photo_200") ? user.getString("photo_200") : Strings.no_photo) + "');");
                                }
                            } catch (JSONException ex) {
                                ex.printStackTrace();
                            }
                            context.onResult(null);
                        }
                    });
                }

                if (group_ids.size() > 0) {
                    context.responses++;
                    groups = new VKRequest("groups.getById", VKParameters.from("group_ids", Easies.join(group_ids)));
                    groups.executeWithListener(new VKRequest.VKRequestListener() {
                        @Override
                        public void onComplete(VKResponse response) {
                            super.onComplete(response);
                            try {
                                JSONArray array = response.json.getJSONObject("response").getJSONArray("items");
                                for (int i = 0; i < array.length(); ++i) {
                                    ContentValues val = new ContentValues();
                                    JSONObject jj = array.getJSONObject(i).getJSONObject("message");
                                    int id = -jj.getInt("id");
                                    val.put(Strings.dialog_id, id);
                                    val.put(Strings.type, Strings.community);
                                    val.put(Strings.date, time.get(id));
                                    db.insertWithOnConflict(Strings.dialogs, null, val, SQLiteDatabase.CONFLICT_REPLACE);
                                    db.execSQL("INSERT OR REPLACE INTO names VALUES (" + Integer.toString(id) + ", " +
                                            "'" + array.getJSONObject(i).getString("name") + "');");
                                    db.execSQL("INSERT OR REPLACE INTO pictures VALUES (" + Integer.toString(id) + ", " +
                                            "'" + (jj.has("photo_200") ? jj.getString("photo_200") : Strings.no_photo) + "');");
                                }
                            } catch (JSONException ex) {
                                ex.printStackTrace();
                            }
                            context.onResult(null);
                        }
                    });
                }
                context.onResult(null);
            }
        });
    }

    @Override
    public void onCreate(SQLiteDatabase data) {
        Log.d(LOG_TAG, "oncreate");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.dialogs +        " (dialog_id INTEGER PRIMARY KEY, type TEXT, date INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.last_message_id +" (dialog_id INTEGER PRIMARY KEY, message_id INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.names +          " (dialog_id INTEGER PRIMARY KEY, name TEXT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.pictures +       " (dialog_id INTEGER PRIMARY KEY, link TEXT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.advanced +       " (dialog_id INTEGER PRIMARY KEY, symbols INT, out INT," +
                "                                                        photos INT, videos INT, walls INT, audios INT);");
        data.execSQL("CREATE TABLE IF NOT EXISTS " + Strings.counts + " (dialog_id INTEGER PRIMARY KEY, counter INT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        //pass TODO maybe, or don't give a fuck about this
    }
}