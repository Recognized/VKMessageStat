package com.vladsaif.vkmessagestat;

import android.app.Application;
import com.vk.sdk.VKSdk;

public class VkStat extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        VKSdk.initialize(this);
    }
}
