package com.vladsaif.vkmessagestat;

import android.app.DownloadManager;
import android.graphics.*;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.VKRequest;
import com.vk.sdk.api.VKResponse;

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
                return -user_id;
            // this is never happened, but thank you java that you are worrying about my code :-*
            default:
                return 666;
        }
    }

    public static String getName(int user_id) {
        if (user_id > 0) {
            VKRequest req = new VKRequest("user.get", VKParameters.from("user_id", Integer.toString(user_id)));
            req.executeWithListener(new VKRequest.VKRequestListener() {
                @Override
                public void onComplete(VKResponse response) {
                    super.onComplete(response);
                    // fuck this architecture i just want to return my response
                }
            });
            Future<>
        }
    }
}
