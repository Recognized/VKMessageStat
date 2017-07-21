package com.vladsaif.vkmessagestat;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ButtonBarLayout;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKError;

public class LoginActivity extends AppCompatActivity {

    private Button login;
    private TextView error;

    @Override
    protected void onStart() {
        super.onStart();
        VKSdk.initialize(this);
        VKAccessToken token = VKAccessToken.tokenFromSharedPreferences(getApplication(), "access_token");
        if (token != null) {
            // TODO dunno whether it's good to start new activity here and will this activity be created or not
            startActivity(new Intent(getApplicationContext(), MainPage.class));
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
                VKSdk.login(running, "messages", "offline");
            }
        });

        error = (TextView) findViewById(R.id.auth_error);
        error.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!VKSdk.onActivityResult(requestCode, resultCode, data, new VKCallback<VKAccessToken>() {
            @Override
            public void onResult(VKAccessToken res) {
                res.saveTokenToSharedPreferences(getApplication(), getString(R.string.token));
                startActivity(new Intent(getApplicationContext(), MainPage.class));
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
}
