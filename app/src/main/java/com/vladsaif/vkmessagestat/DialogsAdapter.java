package com.vladsaif.vkmessagestat;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vk.sdk.api.VKApi;
import com.vk.sdk.api.model.VKApiPhoto;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {

    private HashMap<Integer, DialogData> data;
    private ArrayList<Integer> positionToId;
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    private MainPage.SetImage imageSetter;
    private Context context;
    private static final String here = "adapter";
    private final SQLiteDatabase db;

    public DialogsAdapter(final DbHelper helper, Context context, MainPage.SetImage imageSetter) {
        this.imageSetter = imageSetter;
        this.context = context;
        data = new HashMap<>();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        positionToId = new ArrayList<>();
        db = helper.getWritableDatabase();
        // Copy-paste because I can't have functional interface
        Log.d("db", db.getPath());
        Cursor dialogs = db.rawQuery("SELECT dialog_id, type FROM dialogs", new String[]{});
        Log.d(here, "count " + Integer.toString(dialogs.getCount()));
        if (dialogs.getCount() > 0) {
            dialogs.moveToFirst();
            do {
                Integer id = dialogs.getInt(dialogs.getColumnIndex("dialog_id"));
                positionToId.add(id);
                String type = dialogs.getString(dialogs.getColumnIndex("type"));
                data.put(id, new DialogData(id, Utils.resolveType(type)));
            } while (dialogs.moveToNext());
        }
        dialogs.close();

        Cursor names = db.rawQuery("SELECT dialog_id, name FROM names", new String[]{});
        if (names.getCount() > 0) {
            names.moveToFirst();
            do {
                Integer id = names.getInt(names.getColumnIndex("dialog_id"));
                data.get(id).name = names.getString(names.getColumnIndex("name"));
            } while (names.moveToNext());
        }
        names.close();

        Cursor pictures = db.rawQuery("SELECT dialog_id, link FROM pictures", new String[]{});
        if (pictures.getCount() > 0) {
            pictures.moveToFirst();
            do {
                Integer id = pictures.getInt(pictures.getColumnIndex("dialog_id"));
                data.get(id).link = pictures.getString(pictures.getColumnIndex("link"));
            } while (pictures.moveToNext());
        }
        pictures.close();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout layout;
        public ImageView avatar;
        public TextView title;
        public TextView mcounter;
        public TextView scounter;

        public ViewHolder(View v) {
            super(v);
            layout = (LinearLayout) v;
            avatar = layout.findViewById(R.id.main_page_avatar);
            title = layout.findViewById(R.id.dialog_title);
            mcounter = layout.findViewById(R.id.mcounter);
            scounter = layout.findViewById(R.id.scounter);
        }
    }

    @Override
    public DialogsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dialog, parent, false);
        Log.d(here, "onCreateViewHolder");
        return new DialogsAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(DialogsAdapter.ViewHolder holder, int position) {
        Log.d(here, "onBindViewHolder");
        Integer dialog_id = positionToId.get(position);
        Bitmap image = Utils.loadPic(data.get(dialog_id).link, options, context);
        if (image == null) {
            imageSetter.execute(data.get(dialog_id).link, holder.avatar);
        } else {
            holder.avatar.setImageBitmap(image);
        }
        holder.title.setText(data.get(position).name);
        holder.mcounter.setText("0");
        holder.scounter.setText("0");
    }

    @Override
    public int getItemCount() {
        Log.d(here, "itemcount " + Integer.toString(data.size()));
        return data.size();
    }


}
