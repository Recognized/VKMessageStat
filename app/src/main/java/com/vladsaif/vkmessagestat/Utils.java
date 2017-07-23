package com.vladsaif.vkmessagestat;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by vlad9 on 20.07.2017.
 */
public class Utils {
    public static final String settings = "settings";
    public static final String external_storage = "external_storage";

    public enum DIALOG_TYPE {USER, CHAT, COMMUNITY}

    // Use only for avatar's of users
    public static Bitmap getCircleBitmap(Bitmap source) {
        if (source == null) {
            return null;
        }

        int radius = source.getHeight() / 2;
        int diam = radius * 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        final Shader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        Bitmap targetBitmap = Bitmap.createBitmap(diam, diam, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);

        canvas.drawCircle(radius, radius, radius, paint);

        return targetBitmap;
    }

    public static DIALOG_TYPE resolveTypeBySomeShitThankYouVK(int user_id, int chat_id) {
        if (chat_id != -1) return DIALOG_TYPE.CHAT;
        else if (user_id < 0) return DIALOG_TYPE.COMMUNITY;
        else return DIALOG_TYPE.USER;
    }

    public static int getDialogID(DIALOG_TYPE shit, int user_id, int chat_id) {
        switch (shit) {
            case USER:
                return user_id;
            case CHAT:
                return chat_id + 2000000000;
            case COMMUNITY:
                return user_id;
            // this is never happened, but thank you java that you are worrying about my code :-*
            default:
                return 666;
        }
    }

    public static String join(ArrayList<Integer> ids) {
        StringBuilder temp = new StringBuilder();
        for(int i = 0; i < ids.size()-1; ++i) {
            temp.append(ids.get(i));
            temp.append(',');
        }
        temp.append(ids.get(ids.size()-1));
        return temp.toString();
    }

    // Returning absolute Path with file separator at the end
    public static String getAppAbsolutePath(Context context) {
        SharedPreferences sPref = context.getSharedPreferences(Utils.settings, Context.MODE_PRIVATE);
        File dir = sPref.getBoolean(Utils.external_storage, false) ? context.getExternalFilesDir(null) : context.getFilesDir();
        return dir.getAbsolutePath() + File.separator;
    }

    public static DIALOG_TYPE resolveType(String s) {
        switch (s) {
            case "chat": return DIALOG_TYPE.CHAT;
            case "user": return DIALOG_TYPE.USER;
            default: return DIALOG_TYPE.COMMUNITY;
        }
    }

    public static void savePic(Bitmap pic, String filename, Context context) {
        String dbfile = getAppAbsolutePath(context) + "photos" + File.separator + filename;
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
    }

    public static Bitmap loadPic(String link, BitmapFactory.Options options, Context context) {
        String photoPath = getAppAbsolutePath(context) + "photos" + File.separator + transformLink(link);
        try {
            return BitmapFactory.decodeFile(photoPath, options);
        } catch (Exception ex) {
            return null;
        }

    }

    public static String transformLink(String s) {
        // some magical replacements providing unique filename
        return s.substring(18).replace("/", "");
    }
}
