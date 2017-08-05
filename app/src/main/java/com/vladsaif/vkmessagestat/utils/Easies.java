package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Easies {
    public enum DIALOG_TYPE {USER, CHAT, COMMUNITY}

    private static final String LOG_TAG = "Utils.Easies";
    public static String[] rus_months = new String[]{"янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};

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
        for (int i = 0; i < ids.size() - 1; ++i) {
            temp.append(ids.get(i));
            temp.append(',');
        }
        temp.append(ids.get(ids.size() - 1));
        return temp.toString();
    }

    // Returning absolute Path with file separator at the end
    public static String getAppAbsolutePath(Context context) {
        SharedPreferences sPref = context.getSharedPreferences(Strings.settings, Context.MODE_PRIVATE);
        File dir = sPref.getBoolean(Strings.external_storage, false) ? context.getExternalFilesDir(null) : context.getFilesDir();
        return dir.getAbsolutePath() + File.separator;
    }

    public static String getPhotosPath(Context context) {
        return getAppAbsolutePath(context) + Strings.photos + File.separator;
    }

    public static String getDatabasesPath(Context context) {
        return getAppAbsolutePath(context) + Strings.databases + File.separator;
    }

    public static DIALOG_TYPE resolveType(String s) {
        switch (s) {
            case Strings.chat:
                return DIALOG_TYPE.CHAT;
            case Strings.user:
                return DIALOG_TYPE.USER;
            default:
                return DIALOG_TYPE.COMMUNITY;
        }
    }

    public static void savePic(Bitmap pic, String filename, Context context) {
        String dbfile = getAppAbsolutePath(context) + Strings.photos + File.separator;
        File folder = new File(dbfile);
        folder.mkdirs();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(dbfile + filename);
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
        String photoPath = getPhotosPath(context);
        File folder = new File(photoPath);
        folder.mkdirs();
        try {
            Log.d(LOG_TAG, "picture is loaded");
            return BitmapFactory.decodeFile(photoPath + transformLink(link), options);
        } catch (Exception ex) {
            Log.d(LOG_TAG, "picture isn't loaded, trying download");
            return null;
        }

    }

    public static String transformLink(String s) {
        // some magical replacements providing unique filename
        return s.substring(20).replace("/", "");
    }

    // TODO Some tests, please
    public static String dateToHumanReadable(int date) {
        long current = new Date().getTime();
        long unix_time = date * 1000L;
        SimpleDateFormat day = new SimpleDateFormat("d", Locale.ENGLISH);
        SimpleDateFormat year = new SimpleDateFormat("yyyy", Locale.ENGLISH);
        Date currentDate = new Date(current);
        Date someDate = new Date(unix_time);
        // same day
        if (current - unix_time < 86400 * 1000L && day.format(currentDate).equals(day.format(someDate))) {
            return new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(someDate);
        } else if (current - unix_time < 2 * 86400 * 1000L && day.format(new Date(unix_time + 86400L))
                .equals(day.format(currentDate))) {
            return "вчера";
        } else if (year.format(currentDate).equals(year.format(someDate))) {
            return day.format(someDate) + " " +
                    rus_months[Integer.decode(new SimpleDateFormat("M", Locale.ENGLISH).format(someDate))];
        } else return day.format(someDate) + " " +
                rus_months[Integer.decode(new SimpleDateFormat("M", Locale.ENGLISH).format(someDate))]
                + " " + new SimpleDateFormat("yyyy", Locale.ENGLISH).format(someDate);
    }
}
