package com.vladsaif.vkmessagestat.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.utils.CacheFile;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.SetImageBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {

    private static final String here = "adapter";
    private SparseArray<DialogData> data;
    private ArrayList<DialogData> dialogDataSortedByDate;
    private ArrayList<Integer> positionToId;
    private SparseIntArray positionByMessages;
    private SparseIntArray positionBySymbols;
    private Context context;
    private int currentLoadedDialogs;
    private final int fixedCount = 200;
    boolean sent = false;
    private final LinearLayoutManager linearLayoutManager;

    public DialogsAdapter(Context context, LinearLayoutManager ll) {
        this.context = context;
        linearLayoutManager = ll;
        data = Easies.deserializeData(context);
        dialogDataSortedByDate = new ArrayList<>();
        for (int i = 0; i < data.size(); ++i) {
            dialogDataSortedByDate.add(data.valueAt(i));
        }
        Collections.sort(dialogDataSortedByDate, new Comparator<DialogData>() {
            @Override
            public int compare(DialogData dialogData, DialogData t1) {
                return t1.date - dialogData.date;
            }
        });
        ArrayList<DialogData> sortedByMessage = new ArrayList<>(dialogDataSortedByDate);
        Collections.sort(sortedByMessage, new Comparator<DialogData>() {
            @Override
            public int compare(DialogData dialogData, DialogData t1) {
                return (int) (t1.messages - dialogData.messages);
            }
        });
        ArrayList<DialogData> sortedBySymbols = new ArrayList<>(dialogDataSortedByDate);
        Collections.sort(sortedBySymbols, new Comparator<DialogData>() {
            @Override
            public int compare(DialogData dialogData, DialogData t1) {
                return (int) (t1.symbols - dialogData.symbols);
            }
        });
        positionByMessages = new SparseIntArray();
        for (int i = 0; i < sortedByMessage.size(); ++i) {
            positionByMessages.put(sortedByMessage.get(i).dialog_id, i + 1);
        }
        positionBySymbols = new SparseIntArray();
        for (int i = 0; i < sortedBySymbols.size(); ++i) {
            positionBySymbols.put(sortedBySymbols.get(i).dialog_id, i + 1);
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
        DialogData thisData = dialogDataSortedByDate.get(position);
        holder.position = position;
        holder.title.setText(thisData.name);
        holder.scounter.setText(String.format(Locale.ENGLISH, "%d / %d", thisData.out_symbols, thisData.symbols));
        holder.mcounter.setText(String.format(Locale.ENGLISH, "%d / %d", thisData.out, thisData.messages));
        holder.acounter.setText(String.format(Locale.ENGLISH, "%d", thisData.audios + thisData.videos
                + thisData.walls + thisData.pictures));
        holder.sposition.setText(String.format(Locale.ENGLISH, "%d", positionBySymbols.get(thisData.dialog_id)));
        holder.mposition.setText(String.format(Locale.ENGLISH, "%d", positionByMessages.get(thisData.dialog_id)));
        holder.dateview.setText(Easies.dateToHumanReadable(thisData.date));

        if (SetImage.cached.get(thisData.link) == null) {
            Bitmap fromMemory = CacheFile.loadPic(thisData.link, context);
            if (fromMemory == null) {
                CacheFile.setDefaultImage(holder.avatar, thisData.type, context);
                (new SetImage(holder, position, context)).execute(thisData.link);
            } else {
                holder.avatar.setImageBitmap(fromMemory);
            }
        } else {
            holder.avatar.setImageBitmap(SetImage.cached.get(thisData.link));
        }
    }

    /*public RecyclerView.OnScrollListener scrolling = new RecyclerView.OnScrollListener() {
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
                m.arg2 = currentLoadedDialogs + 1;
                context.requestHandler.sendMessage(m);
                sent = true;
            }
        }
    };*/

    @Override
    public int getItemCount() {
        Log.d(here, Integer.toString(data.size()));
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout layout;
        public ImageView avatar;
        public TextView title;
        public TextView mcounter;
        public TextView scounter;
        public TextView acounter;
        public TextView mposition;
        public TextView sposition;
        public TextView dateview;
        public int position;

        public ViewHolder(View v) {
            super(v);
            layout = (LinearLayout) v;
            avatar = layout.findViewById(R.id.main_page_avatar);
            title = layout.findViewById(R.id.dialog_title);
            mcounter = layout.findViewById(R.id.messages_count);
            scounter = layout.findViewById(R.id.symbols_count);
            acounter = layout.findViewById(R.id.attachments_count);
            mposition = layout.findViewById(R.id.message_position);
            sposition = layout.findViewById(R.id.symbols_position);
            dateview = layout.findViewById(R.id.date);
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
