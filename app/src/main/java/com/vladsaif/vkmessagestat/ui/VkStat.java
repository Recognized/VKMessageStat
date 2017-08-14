package com.vladsaif.vkmessagestat.ui;

import android.app.Application;
import android.util.Log;
import com.vk.sdk.VKSdk;

public class VkStat extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        VKSdk.customInitialize(this, 6096377, "5.67");
        Log.d("Api version", VKSdk.getApiVersion());
    }
}
