package com.vladsaif.vkmessagestat;

import android.graphics.Bitmap;
import android.net.LinkAddress;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKApi;
import com.vk.sdk.api.VKParameters;
import com.vk.sdk.api.methods.VKApiUsers;

import java.util.Map;

public class MainPage extends AppCompatActivity {

    private VKAccessToken token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        // TODO dont't forget to change title
        // TODO correct settings
        setSupportActionBar(toolbar);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), "access_token");
        LayoutInflater inflater = getLayoutInflater();
        ListView dialogs = (ListView) findViewById(R.id.dialogs);
        LinearLayout dialog = (LinearLayout) inflater.inflate(R.layout.dialog, dialogs, false);
        fillDialogView(dialog, null, "Name", 1, 1);
        dialogs.addView(dialog);


    }

    private void fillDialogView(LinearLayout dialog, Bitmap avatar, String name, Integer mcounter, Integer scounter) {
        ImageView image = dialog.findViewById(R.id.main_page_avatar);
        image.setImageBitmap(Utils.getCircleBitmap(avatar));
        TextView txt = dialog.findViewById(R.id.dialog_title);
        txt.setText(name);
    }

// TODO do smth here
//    class DialogInfo {
//        private View view;
//
//        public View getView() {
//            return view;
//        }
//
//        public DialogInfo(dialog_id,)
//
//    }
}
