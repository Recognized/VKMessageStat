package com.vladsaif.vkmessagestat.ui;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.VKScope;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKError;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LoginActivity extends AppCompatActivity {
    public final String LOG_TAG = LoadingActivity.class.getSimpleName();
    private Button login;
    private TextView error;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!VKSdk.onActivityResult(requestCode, resultCode, data, new VKCallback<VKAccessToken>() {
            @Override
            public void onResult(VKAccessToken res) {
                res.saveTokenToSharedPreferences(getApplication(), Strings.access_token);
                nextActivity(getSharedPreferences(Strings.settings, MODE_PRIVATE));
            }

            @Override
            public void onError(VKError er) {
                // Произошла ошибка авторизации (например, пользователь запретил авторизацию)
                error.setVisibility(View.VISIBLE);
            }
        })) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        VKAccessToken token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        if (token != null) {
            SharedPreferences sPref = getSharedPreferences(Strings.settings, MODE_PRIVATE);
            if (!sPref.contains(Strings.external_storage)) {
                SharedPreferences.Editor edit = sPref.edit();
                try {
                    File test = new File(getExternalFilesDir(null), "test");
                    OutputStream os = new FileOutputStream(test);
                    Log.d(LOG_TAG, "using external storage");
                    edit.putBoolean(Strings.external_storage, true);
                } catch (IOException ex) {
                    Log.d(LOG_TAG, "using internal storage");
                    edit.putBoolean(Strings.external_storage, false);
                }
                edit.apply();
            }
            nextActivity(sPref);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        login = (Button) findViewById(R.id.sign_in_button);

        final Activity running = this;
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VKSdk.login(running, VKScope.MESSAGES, VKScope.OFFLINE);
            }
        });

        error = (TextView) findViewById(R.id.auth_error);
        error.setVisibility(View.GONE);
    }

    public void nextActivity(SharedPreferences sPref) {
        boolean advanced_stat = sPref.getBoolean(Strings.stat_mode, false);
        if(!advanced_stat) {
            Intent intent = new Intent(getApplicationContext(), MessagesCollectorNew.class);
            intent.putExtra(Strings.commandType, Strings.commandDump);
            startService(intent);
            Intent openProgress = new Intent(getApplicationContext(), LoadingActivity.class);
            startActivity(openProgress);
        } else {
            startActivity(new Intent(this, MainPage.class));
        }
    }
}
