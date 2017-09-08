package com.vladsaif.vkmessagestat.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
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
import com.vladsaif.vkmessagestat.db.GlobalData;
import com.vladsaif.vkmessagestat.ui.DialogDetailActivity;
import com.vladsaif.vkmessagestat.ui.DialogDetailFragment;
import com.vladsaif.vkmessagestat.ui.MainPage;
import com.vladsaif.vkmessagestat.utils.CacheFile;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.SetImageBase;
import com.vladsaif.vkmessagestat.utils.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {
    private static final String LOG_TAG = DialogsAdapter.class.getSimpleName();
    private static SparseArray<DialogData> data;
    private static GlobalData globalData;
    private ArrayList<DialogData> dialogData;
    private SparseIntArray positionByMessages;
    private SparseIntArray positionBySymbols;
    private Context context;
    private boolean calledFromConstructor;
    private int current_selected = -1;

    private RecyclerView mRecyclerView;

    public static final int ORDER_DESC = 1;
    public static final int ORDER_ASC = 2;
    public static final int ORDER_TIME_DESC = 3;
    public static final int ORDER_TIME_ASC = 4;

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int itemPosition = mRecyclerView.getChildLayoutPosition(view);
            if (current_selected != -1) {
                View v = mRecyclerView.getLayoutManager().findViewByPosition(current_selected);
                if (v != null) {
                    v.setBackgroundColor(ContextCompat.getColor(context, R.color.vk_white));
                    v.findViewById(R.id.stat_table).setBackgroundResource(R.drawable.bottom_line);
                }
            }
            current_selected = itemPosition;
            int blue = ContextCompat.getColor(context, R.color.selected_menu);
            view.setBackgroundColor(blue);
            view.findViewById(R.id.stat_table).setBackgroundColor(blue);
            if (context instanceof MainPage) {
                MainPage mainPage = (MainPage) context;
                if (mainPage.mTwoPane) {
                    mainPage.detailContainer.removeAllViews();
                    Bundle arguments = new Bundle();
                    arguments.putInt(Strings.dialog_id, dialogData.get(itemPosition).dialog_id);
                    DialogDetailFragment fragment = new DialogDetailFragment();
                    fragment.setArguments(arguments);
                    mainPage.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.dialog_detail_container, fragment)
                            .commit();

                } else {
                    Intent toDetailFlow = new Intent(context, DialogDetailActivity.class);
                    toDetailFlow.putExtra(Strings.dialog_id, dialogData.get(itemPosition).dialog_id);
                    mainPage.startActivity(toDetailFlow);
                }
            }
        }
    };


    // TODO make constructor from savedInstance state

    public DialogsAdapter(Context context, int ORDER_MODE, RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        this.context = context;
        if (globalData == null) {
            globalData = new GlobalData(context);
        }
        if (data == null) {
            reloadDialogData(context);
        }
        if (dialogData == null) {
            reformData(ORDER_MODE);
        }
    }

    public void sortData(int ORDER_MODE) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("adapter_order", ORDER_MODE);
        editor.apply();
        Comparator<DialogData> comparator = null;
        switch (ORDER_MODE) {
            case DialogsAdapter.ORDER_TIME_DESC:
                comparator = new Comparator<DialogData>() {
                    @Override
                    public int compare(DialogData dialogData, DialogData t1) {
                        return t1.date - dialogData.date;
                    }
                };
                break;
            case DialogsAdapter.ORDER_TIME_ASC:
                comparator = new Comparator<DialogData>() {
                    @Override
                    public int compare(DialogData dialogData, DialogData t1) {
                        return dialogData.date - t1.date;
                    }
                };
                break;
            case DialogsAdapter.ORDER_ASC:
                comparator = new Comparator<DialogData>() {
                    @Override
                    public int compare(DialogData dialogData, DialogData t1) {
                        return (int) (dialogData.messages - t1.messages);
                    }
                };
                break;
            case DialogsAdapter.ORDER_DESC:
                comparator = new Comparator<DialogData>() {
                    @Override
                    public int compare(DialogData dialogData, DialogData t1) {
                        return (int) (t1.messages - dialogData.messages);
                    }
                };
        }
        Collections.sort(dialogData, comparator);
        if (!calledFromConstructor) notifyDataSetChanged();
    }

    @Override
    public DialogsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dialog, parent, false);
        v.setOnClickListener(clickListener);
        Log.d(LOG_TAG, "onCreateViewHolder");
        return new DialogsAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(DialogsAdapter.ViewHolder holder, int position) {
        Log.d(LOG_TAG, "onBindViewHolder");
        DialogData thisData = dialogData.get(position);
        holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.vk_white));
        holder.itemView.findViewById(R.id.stat_table).setBackgroundResource(R.drawable.bottom_line);
        holder.position = position;
        holder.title.setText(thisData.name);
        holder.other_name.setText(Easies.getShortName(thisData.type, thisData.name));
        switch (positionByMessages.get(thisData.dialog_id)) {
            case 1:
                setRing(holder.ring, R.drawable.first);
                break;
            case 2:
                setRing(holder.ring, R.drawable.second);
                break;
            case 3:
                setRing(holder.ring, R.drawable.third);
                break;
            default:
                holder.ring.setVisibility(View.GONE);
        }
        holder.scounter_out.setText(Long.toString(thisData.out_symbols));
        holder.mcounter_out.setText(Long.toString(thisData.out));
        holder.scounter_in.setText(Long.toString(thisData.symbols - thisData.out_symbols));
        holder.mcounter_in.setText(Long.toString(thisData.messages - thisData.out));
        holder.acounter_out.setText(Long.toString(thisData.audios + thisData.videos
                + thisData.walls + thisData.pictures + thisData.link_attachms + thisData.docs + thisData.gifts));
        holder.acounter_in.setText(Long.toString(thisData.other_audios
                + thisData.other_videos + thisData.other_walls + thisData.other_pictures
                + thisData.other_link_attachms + thisData.other_docs + thisData.other_gifts));
        holder.dateview.setText(Easies.dateToHumanReadable(thisData.date));
            CacheFile.setImage(thisData, new SetImage(holder, position, context));
    }

    public void reformData(int ORDER_MODE) {
        dialogData = new ArrayList<>();
        for (int i = 0; i < data.size(); ++i) {
            dialogData.add(data.valueAt(i));
        }
        Log.d(LOG_TAG, "" + data.size());
        calledFromConstructor = true;
        sortData(ORDER_MODE);
        calledFromConstructor = false;
        ArrayList<DialogData> sortedByMessage = new ArrayList<>(dialogData);
        sortedByMessage.remove(data.get(DialogData.GLOBAL_DATA_ID));
        Collections.sort(sortedByMessage, new Comparator<DialogData>() {
            @Override
            public int compare(DialogData dialogData, DialogData t1) {
                return (int) (t1.messages - dialogData.messages);
            }
        });
        ArrayList<DialogData> sortedBySymbols = new ArrayList<>(dialogData);
        sortedBySymbols.remove(data.get(DialogData.GLOBAL_DATA_ID));
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
        positionByMessages.put(DialogData.GLOBAL_DATA_ID, sortedByMessage.size());
        positionBySymbols = new SparseIntArray();
        for (int i = 0; i < sortedBySymbols.size(); ++i) {
            positionBySymbols.put(sortedBySymbols.get(i).dialog_id, i + 1);
        }
        positionBySymbols.put(DialogData.GLOBAL_DATA_ID, sortedBySymbols.size());
    }

    public void reloadData(int ORDER_MODE) {
        globalData = new GlobalData(context);
        reloadDialogData(context);
        reformData(ORDER_MODE);
    }

    private void setRing(ImageView view, int resource) {
        view.setVisibility(View.VISIBLE);
        view.setImageBitmap(BitmapFactory.decodeResource(context.getResources(), resource));
    }

    @Override
    public int getItemCount() {
        Log.d(LOG_TAG, Integer.toString(dialogData.size()));
        return dialogData.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout layout;
        public ImageView avatar;
        public ImageView ring;
        public TextView title;
        public TextView mcounter_out;
        public TextView scounter_out;
        public TextView mcounter_in;
        public TextView scounter_in;
        public TextView other_name;
        public TextView acounter_in;
        public TextView acounter_out;
        public TextView dateview;
        public int position;

        public ViewHolder(View v) {
            super(v);
            layout = (LinearLayout) v;
            avatar = layout.findViewById(R.id.main_page_avatar);
            ring = layout.findViewById(R.id.ring);
            title = layout.findViewById(R.id.dialog_title);
            mcounter_out = layout.findViewById(R.id.your_messages);
            scounter_out = layout.findViewById(R.id.your_symbols);
            mcounter_in = layout.findViewById(R.id.other_messages);
            scounter_in = layout.findViewById(R.id.other_symbols);
            other_name = layout.findViewById(R.id.other_name);
            acounter_out = layout.findViewById(R.id.attachments_count);
            acounter_in = layout.findViewById(R.id.other_attachments);
            dateview = layout.findViewById(R.id.date);
        }
    }

    public class SetImage extends SetImageBase {
        private ViewHolder holder;
        private int mPosition;

        SetImage(ViewHolder holder, int currentHolderPosition, Context context) {
            super(holder.avatar, context);
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

    public static GlobalData getGlobalData(Context context) {
        if (globalData != null) return globalData;
        else {
            globalData = new GlobalData(context);
            return globalData;
        }
    }

    private static void reloadDialogData(Context context) {
        data = Easies.deserializeData(context.getApplicationContext());
    }

    public static SparseArray<DialogData> getData(Context context) {
        if (data != null) return data;
        else {
            reloadDialogData(context);
            return data;
        }
    }
}
