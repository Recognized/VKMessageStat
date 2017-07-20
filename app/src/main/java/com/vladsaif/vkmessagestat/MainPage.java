package com.vladsaif.vkmessagestat;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.vk.sdk.VKSdk;

public class MainPage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        // TODO dont't forget to change title
        // TODO correct settings
        setSupportActionBar(toolbar);


    }

    class DialogInfo {
        private View view;

        public View getView() {
            return view;
        }

        public DialogInfo(dialog_id,)

    }
}
