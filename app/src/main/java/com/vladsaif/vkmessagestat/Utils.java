package com.vladsaif.vkmessagestat;

import android.graphics.*;

/**
 * Created by vlad9 on 20.07.2017.
 */
public class Utils {
    public static final String settings = "settings";
    public static final String external_storage = "external_storage";
    // Use only for avatar's of users
    public static Bitmap getCircleBitmap(Bitmap source)
    {
        if (source == null)
        {
            return null;
        }

        int radius = source.getHeight()/2;
        int diam = radius * 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        final Shader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        Bitmap targetBitmap = Bitmap.createBitmap(diam, diam, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);

        canvas.drawCircle(radius, radius, radius, paint);

        return targetBitmap;
    }
}
