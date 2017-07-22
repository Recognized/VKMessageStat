package com.vladsaif.vkmessagestat;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class DbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    public SQLiteDatabase db;

    public DbHelper(final Context context, String databaseName) {
        super(new DatabaseContext(context), databaseName, null, DATABASE_VERSION);
        db = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase data) {
        data.execSQL("CREATE TABLE IF NOT EXISTS dialogs (dialog_id INTEGER PRIMARY KEY, name TEXT, " +
                "mcounter INT, scounter INT)");
        data.execSQL("CREATE TABLE IF NOT EXISTS last_message_id (dialog_id INTEGER PRIMARY KEY, message_id INT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        //pass TODO maybe, or don't give a fuck about this
    }

    public static void downloadMessages(SQLiteDatabase db, Context context) {
        db.beginTransaction();
        // TODO maybe I need fix locale somehow
        // TODO this is just a stub for a real shit
        VKRequest req = new VKRequest("messages.getDialogs", VKParameters.from("count", "20"));
        req.executeWithListener(new VKRequest.VKRequestListener() {
            @Override
            public void onComplete(VKResponse response) {
                super.onComplete(response);
                try {
                    JSONArray items = response.json.getJSONArray("items");
                    for (int i = 0; i < items.length(); ++i) {
                        JSONObject message = items.getJSONObject(i);
                        int chat_id = message.has("chat_id") ? message.getInt("chat_id") : -1;
                        int user_id = message.getInt("user_id");
                        Utils.DIALOG_TYPE type = Utils.resolveTypeBySomeShitThankYouVK(user_id, chat_id);
                        int dialog_id = Utils.getDialogID(type, user_id, chat_id);
                        String name = message.has("title") ? message.getString("title") : Utils.getName(user_id);



                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static String savePic(Bitmap pic, String username, Context context) {
        SharedPreferences sPref = context.getSharedPreferences(Utils.settings, Context.MODE_PRIVATE);
        File dir = sPref.getBoolean(Utils.external_storage, false) ? context.getExternalFilesDir(null) : context.getFilesDir();
        String dbfile = dir.getAbsolutePath() + File.separator + "photos" + File.separator + username;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(dbfile);
            pic.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dbfile;
    }
}