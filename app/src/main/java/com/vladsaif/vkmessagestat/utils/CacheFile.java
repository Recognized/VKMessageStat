package com.vladsaif.vkmessagestat.utils;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DialogData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class CacheFile {
    private static final String LOG_TAG = CacheFile.class.getSimpleName();
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    private static Bitmap chatPlaceholder;
    private static Bitmap otherPlaceholder;

    static {
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    public static Bitmap loadBitmap(String link, Context context) {
            Bitmap bitmap;
            if (!link.equals("no_photo")) {
                try {
                    InputStream inputStream = new URL(link).openStream();   // Download Image from URL
                    bitmap = Easies.getCircleBitmap(BitmapFactory.decodeStream(inputStream));       // Decode Bitmap
                    inputStream.close();
                    savePic(bitmap, Easies.transformLink(link), context.getApplicationContext());
                } catch (Exception e) {
                    throw new RuntimeException(e.toString());
                }
            } else {
                bitmap = null;
            }
            return bitmap;
    }

    public static Bitmap loadPic(String link, Context context) {
        String photoPath = Easies.getPhotosPath(context);
        File folder = new File(photoPath);
        folder.mkdirs();
        try {
            Log.d(LOG_TAG, "picture is loaded");
            return BitmapFactory.decodeFile(photoPath + Easies.transformLink(link), options);
        } catch (Exception ex) {
            Log.d(LOG_TAG, "picture isn't loaded, trying download" + ex.toString());
            return null;
        }
    }

    public static void savePic(Bitmap pic, String filename, Context context) {
        String dbfile = Easies.getAppAbsolutePath(context) + Strings.photos + File.separator;
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

    public static void setDefaultImage(ImageView view, Easies.DIALOG_TYPE type, Context context) {
        if (type == Easies.DIALOG_TYPE.CHAT) {
            if (chatPlaceholder == null) {
                chatPlaceholder = Easies.getCircleBitmap(
                        BitmapFactory.decodeResource(context.getApplicationContext().getResources(), R.drawable.community_100));
            }
            view.setImageBitmap(chatPlaceholder);
        } else {
            if (otherPlaceholder == null) {
                otherPlaceholder = Easies.getCircleBitmap(
                        BitmapFactory.decodeResource(context.getApplicationContext().getResources(), R.drawable.camera_100));
            }
            view.setImageBitmap(otherPlaceholder);
        }
    }

    public static void setImage(DialogData thisData, SetImageBase setter) {
        if (SetImageBase.cached.get(thisData.link) == null) {
            Bitmap fromMemory = CacheFile.loadPic(thisData.link, setter.context);
            if (fromMemory == null) {
                CacheFile.setDefaultImage(setter.view, thisData.type, setter.context);
                setter.execute(thisData.link);
            } else {
                setter.view.setImageBitmap(fromMemory);
            }
        } else {
            setter.view.setImageBitmap(SetImageBase.cached.get(thisData.link));
        }
    }
}
