package com.vladsaif.vkmessagestat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.*;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.db.DialogData;

import java.io.*;
import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

public class Easies {


    public enum DIALOG_TYPE {USER, CHAT, COMMUNITY;}
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
        File f = new File(getAppAbsolutePath(context) + Strings.photos + File.separator);
        f.mkdirs();
        return f.getAbsolutePath() + File.separator;
    }

    public static String getDatabasesPath(Context context) {
        File f = new File(getAppAbsolutePath(context) + Strings.databases + File.separator);
        f.mkdirs();
        return f.getAbsolutePath() + File.separator;
    }

    public static String getTextDataPath(Context context) {
        File f = new File(getAppAbsolutePath(context) + Strings.textdata + File.separator);
        f.mkdirs();
        return f.getAbsolutePath() + File.separator;
    }

    public static String getSerializablePath(Context context) {
        File f = new File(getAppAbsolutePath(context) + "objects" + File.separator);
        f.mkdirs();
        return f.getAbsolutePath() + File.separator;
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
                    rus_months[Integer.decode(new SimpleDateFormat("M", Locale.ENGLISH).format(someDate)) - 1];
        } else return day.format(someDate) + " " +
                rus_months[Integer.decode(new SimpleDateFormat("M", Locale.ENGLISH).format(someDate))-1]
                + " " + new SimpleDateFormat("yyyy", Locale.ENGLISH).format(someDate);
    }

    public static void imageViewAnimatedChange(final ImageView v, final Bitmap new_image, Context context) {
        final Animation anim_out = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
        final Animation anim_in = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
        anim_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                v.setImageBitmap(new_image);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                    }
                });
                v.startAnimation(anim_in);
            }
        });
        v.startAnimation(anim_out);
    }

    public static void setCustomFont(View textViewOrButton, Context ctx, AttributeSet attrs, int[] attributeSet, int fontId) {
        TypedArray a = ctx.obtainStyledAttributes(attrs, attributeSet);
        String customFont = a.getString(fontId);
        setCustomFont(textViewOrButton, ctx, customFont);
        a.recycle();
    }

    public static boolean setMonospaceFont(TextView view, Context context) {
        return setCustomFont(view, context, "mono.ttf");
    }

    private static boolean setCustomFont(View textView, Context ctx, String asset) {
        if (TextUtils.isEmpty(asset))
            return false;
        Typeface tf = null;
        try {
            tf = getFont(ctx, asset);
            if (textView instanceof TextView) {
                ((TextView) textView).setTypeface(tf);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not get typeface: " + asset, e);
            return false;
        }

        return true;
    }

    private static final Hashtable<String, SoftReference<Typeface>> fontCache = new Hashtable<String, SoftReference<Typeface>>();

    public static Typeface getFont(Context c, String name) {
        synchronized (fontCache) {
            if (fontCache.get(name) != null) {
                SoftReference<Typeface> ref = fontCache.get(name);
                if (ref.get() != null) {
                    return ref.get();
                }
            }

            Typeface typeface = Typeface.createFromAsset(
                    c.getAssets(),
                    "fonts/" + name
            );
            fontCache.put(name, new SoftReference<Typeface>(typeface));

            return typeface;
        }
    }

    public static String fileName = "dialogData.out";

    public static SparseArray<DialogData> deserializeData(Context context) {
        File dialogDataFile = new File(Easies.getSerializablePath(context) + fileName);
        ArrayList<DialogData> prevDialogData = null;
        if (dialogDataFile.exists()) {
            try {
                ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(dialogDataFile));
                prevDialogData = (ArrayList<DialogData>) inputStream.readObject();
                inputStream.close();
            } catch (IOException ex) {
                Log.wtf(LOG_TAG, ex);
            } catch (ClassNotFoundException cnfe) {
                Log.wtf(LOG_TAG, cnfe);
            }
        } else {
            return new SparseArray<>();
        }
        SparseArray<DialogData> ans = new SparseArray<>();
        for (DialogData d : prevDialogData) {
            if (d != null) ans.put(d.dialog_id, d);
        }
        return ans;
    }

    public static void serializeData(SparseArray<DialogData> data, Context context) {
        ArrayList<DialogData> ser = new ArrayList<>();
        for (int i = 0; i < data.size(); ++i) {
            ser.add(data.valueAt(i));
        }
        try {
            File dialogDataFile = new File(Easies.getSerializablePath(context) + fileName);
            if (dialogDataFile.exists()) Log.d(LOG_TAG, "File exists");
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(dialogDataFile));
            outputStream.writeObject(ser);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            Log.wtf(LOG_TAG, ex);
        }
    }
}
