package com.vladsaif.vkmessagestat.adapters;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.services.VKWorker;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.*;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {

    private static final String here = "adapter";
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    private final SQLiteDatabase db;
    private SparseArray<DialogData> data;
    private ArrayList<Integer> positionToId;
    private HashMap<ImageView, String> carouselPics;
    private MainPage context;
    private int currentLoadedDialogs;
    private final int fixedCount = 200;
    boolean sent = false;
    private final LinearLayoutManager linearLayoutManager;
    private Bitmap chatPlaceholder;
    private Bitmap otherPlaceholder;

    public DialogsAdapter(final DbHelper helper, MainPage context, LinearLayoutManager ll) {
        this.context = context;
        linearLayoutManager = ll;
        carouselPics = new HashMap<>();
        data = new SparseArray<>();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        positionToId = new ArrayList<>();
        db = helper.getWritableDatabase();
        chatPlaceholder = Easies.getCircleBitmap(
                BitmapFactory.decodeResource(context.getApplicationContext().getResources(), R.drawable.community_100));
        otherPlaceholder = Easies.getCircleBitmap(
                BitmapFactory.decodeResource(context.getApplicationContext().getResources(), R.drawable.camera_100));
        fetchData();
        currentLoadedDialogs = positionToId.size();
        context.dumper.setOnFinishGetDialogs(fetchNewData);
    }

    public void fetchData() {
        Cursor dialogs = db.rawQuery("SELECT dialog_id, type, date FROM dialogs ORDER BY date DESC;", new String[]{});
        Log.d(here, "count " + Integer.toString(dialogs.getCount()));
        positionToId = new ArrayList<>();
        if (dialogs.moveToFirst()) {
            do {
                Integer id = dialogs.getInt(dialogs.getColumnIndex("dialog_id"));
                positionToId.add(id);
                String type = dialogs.getString(dialogs.getColumnIndex("type"));
                data.put(id, new DialogData(id, Easies.resolveType(type)));
            } while (dialogs.moveToNext());
        }
        dialogs.close();

        Cursor names = db.rawQuery("SELECT dialog_id,name FROM names;", new String[]{});
        if (names.moveToFirst()) {
            do {
                Integer id = names.getInt(names.getColumnIndex("dialog_id"));
                data.get(id).name = names.getString(names.getColumnIndex("name"));
                Log.d("names", data.get(id).name);
            } while (names.moveToNext());
        }
        names.close();

        Cursor pictures = db.rawQuery("SELECT dialog_id, link FROM pictures;", new String[]{});
        if (pictures.moveToFirst()) {
            do {
                Integer id = pictures.getInt(pictures.getColumnIndex("dialog_id"));
                data.get(id).link = pictures.getString(pictures.getColumnIndex("link"));
            } while (pictures.moveToNext());
        }
        pictures.close();

        Cursor counts = db.rawQuery("SELECT dialog_id, counter FROM counts;", new String[]{});
        if(counts.moveToFirst()) {
            do {
                Integer id = counts.getInt(counts.getColumnIndex("dialog_id"));
                data.get(id).messages = counts.getInt(counts.getColumnIndex("counter"));
            } while (counts.moveToNext());
        }
        counts.close();
    }

    private Runnable fetchNewData =  new Runnable() {
        @Override
        public void run() {
            Log.d("does it happen", "null");
            sent = false;
            fetchData();
            currentLoadedDialogs = data.size();
            notifyDataSetChanged();
        }
    };

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
        holder.position = position;
        if(data.get(dialog_id).type == Easies.DIALOG_TYPE.CHAT) {
            holder.avatar.setImageBitmap(chatPlaceholder);
        } else {
            holder.avatar.setImageBitmap(otherPlaceholder);
        }
        String link = data.get(dialog_id).link;
        carouselPics.put(holder.avatar, link);
        Bitmap image = Easies.loadPic(link, options, context);
        if (image == null) {
            (new SetImage()).execute(new AsyncParam(data.get(dialog_id).link, holder, position, null));
        } else {
            holder.avatar.setImageBitmap(image);
        }
        holder.title.setText(data.get(dialog_id).name);
        /*if(data.get(dialog_id).messages != null) {
            holder.mcounter.setText(data.get(dialog_id).messages);
        }*/
        holder.scounter.setText("-");
    }

    public RecyclerView.OnScrollListener scrolling = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int totalItemCount = linearLayoutManager.getItemCount();
            int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
            if (!sent && totalItemCount <= (lastVisibleItem + 10)) {
                sent = true;
                Message m = context.requestHandler.obtainMessage();
                m.what = VKWorker.GET_DIALOGS;
                m.arg1 = fixedCount;
                m.arg2 =  currentLoadedDialogs + 1;
                context.requestHandler.sendMessage(m);
                sent = true;
            }
        }
    };

    @Override
    public int getItemCount() {
        Log.d(here, Integer.toString(data.size()));
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout layout;
        public ImageView avatar;
        public TextView title;
        public ProgressBar mcounter;
        public TextView scounter;
        public int position;

        public ViewHolder(View v) {
            super(v);
            layout = (LinearLayout) v;
            avatar = layout.findViewById(R.id.main_page_avatar);
            title = layout.findViewById(R.id.dialog_title);
            mcounter = layout.findViewById(R.id.progressBar2);
            scounter = layout.findViewById(R.id.scounter);
        }
    }

    public class SetImage extends AsyncTask<AsyncParam, Void, AsyncParam> {
        @Override
        protected AsyncParam doInBackground(AsyncParam[] params) {
            String link = params[0].str;
            Bitmap bitmap = null;
            if (!link.equals("no_photo")) {
                try {
                    InputStream inputStream = new URL(link).openStream();   // Download Image from URL
                    bitmap = Easies.getCircleBitmap(BitmapFactory.decodeStream(inputStream));       // Decode Bitmap
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Easies.savePic(bitmap, Easies.transformLink(link), context.getApplicationContext() );
            } else {
                bitmap = null;
            }
            return new AsyncParam(link, params[0].holder, params[0].mPosition, bitmap);
        }
        protected void onProgressUpdate(Void... params) {
        }

        protected void onPostExecute(AsyncParam result) {
            if(result.holder.position == result.mPosition && result.bitmap != null){
                ImageViewAnimatedChange(result.holder.avatar, result.bitmap);
            }
        }
    }

    public class SetMessages extends AsyncTask<ViewHolder, Void, Integer> {
        @Override
        protected Integer doInBackground(ViewHolder... viewHolders) {
            
            return null;
        }
    }


    public void ImageViewAnimatedChange(final ImageView v, final Bitmap new_image) {
        final Animation anim_out = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
        final Animation anim_in  = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
        anim_out.setAnimationListener(new Animation.AnimationListener()
        {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation)
            {
                v.setImageBitmap(new_image);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                    @Override public void onAnimationEnd(Animation animation) {}
                });
                v.startAnimation(anim_in);
            }
        });
        v.startAnimation(anim_out);
    }
}
