package com.vladsaif.vkmessagestat.ui;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKCallback;
import com.vk.sdk.VKScope;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKApiConst;
import com.vk.sdk.api.VKError;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.Strings;

public class LoginActivity extends AppCompatActivity {

    private Button login;
    private TextView error;

    @Override
    protected void onStart() {
        super.onStart();
        VKAccessToken token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        if (token != null) {
            startActivity(new Intent(this, MainPage.class));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!VKSdk.onActivityResult(requestCode, resultCode, data, new VKCallback<VKAccessToken>() {
            @Override
            public void onResult(VKAccessToken res) {
                res.saveTokenToSharedPreferences(getApplication(), Strings.access_token);
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
