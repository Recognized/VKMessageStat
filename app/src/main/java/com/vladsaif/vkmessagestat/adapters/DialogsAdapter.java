package com.vladsaif.vkmessagestat.adapters;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DbHelper;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.services.VKWorker;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.CacheFile;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.SetImageBase;

import java.util.ArrayList;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {

    private static final String here = "adapter";
    private final SQLiteDatabase db;
    private SparseArray<DialogData> data;
    private ArrayList<Integer> positionToId;
    private MainPage context;
    private int currentLoadedDialogs;
    private final int fixedCount = 200;
    boolean sent = false;
    private final LinearLayoutManager linearLayoutManager;

    public DialogsAdapter(final DbHelper helper, MainPage context, LinearLayoutManager ll) {
        this.context = context;
        linearLayoutManager = ll;
        data = new SparseArray<>();
        positionToId = new ArrayList<>();
        db = helper.getWritableDatabase();
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
        holder.title.setText(data.get(dialog_id).name);
        holder.scounter.setText("-");
        String link = data.get(dialog_id).link;
        CacheFile.setDefaultImage(holder.avatar, data.get(dialog_id).type, context);
        (new SetImage(holder, position, context)).execute(link);
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

    public class SetImage extends SetImageBase {
        private ViewHolder holder;
        private int mPosition;

        public SetImage(ViewHolder holder, int currentHolderPosition, Context context) {
            super(context);
            this.holder = holder;
            this.mPosition = currentHolderPosition;
        }

        @Override
        protected void onPostExecute(Bitmap image) {
            if (holder.position == mPosition && image != null) {
                Easies.imageViewAnimatedChange(holder.avatar, image, context);
            }
        }
    }

}
