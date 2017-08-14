package com.vladsaif.vkmessagestat.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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
import java.util.Locale;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {
    private static final String LOG_TAG = DialogsAdapter.class.getSimpleName();
    public static SparseArray<DialogData> data;
    private ArrayList<DialogData> dialogData;
    private SparseIntArray positionByMessages;
    private SparseIntArray positionBySymbols;
    private Context context;
    private boolean calledFromConstructor;

    private RecyclerView mRecyclerView;

    public static final int ORDER_DESC = 1;
    public static final int ORDER_ASC = 2;
    public static final int ORDER_TIME_DESC = 3;
    public static final int ORDER_TIME_ASC = 4;

    /*if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putString(ItemDetailFragment.ARG_ITEM_ID, holder.mItem.id);
                        ItemDetailFragment fragment = new ItemDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.item_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, ItemDetailActivity.class);
                        intent.putExtra(ItemDetailFragment.ARG_ITEM_ID, holder.mItem.id);

                        context.startActivity(intent);
                    }
                    */

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int itemPosition = mRecyclerView.getChildLayoutPosition(view);
            if (context instanceof MainPage) {
                MainPage mainPage = (MainPage) context;
                if (mainPage.mTwoPane) {
                    mainPage.detailContainer.removeAllViews();
                    Bundle arguments = new Bundle();
                    arguments.putInt(Strings.dialog_id, itemPosition);
                    DialogDetailFragment fragment = new DialogDetailFragment();
                    fragment.setArguments(arguments);
                    mainPage.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.item_detail_container, fragment)
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
        data = Easies.deserializeData(context);
        dialogData = new ArrayList<>();
        for (int i = 0; i < data.size(); ++i) {
            dialogData.add(data.valueAt(i));
        }
        calledFromConstructor = true;
        sortData(ORDER_MODE);
        calledFromConstructor = false;
        ArrayList<DialogData> sortedByMessage = new ArrayList<>(dialogData);
        Collections.sort(sortedByMessage, new Comparator<DialogData>() {
            @Override
            public int compare(DialogData dialogData, DialogData t1) {
                return (int) (t1.messages - dialogData.messages);
            }
        });
        ArrayList<DialogData> sortedBySymbols = new ArrayList<>(dialogData);
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
        Log.d("pos", Integer.toString(sortedByMessage.get(2).dialog_id));
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
        holder.position = position;
        holder.title.setText(thisData.name);
        holder.other_name.setText(getShortName(thisData.type, thisData.name));
        switch (positionByMessages.get(thisData.dialog_id)) {
            case 1:
                setRing(holder.ring, R.drawable.first);
                break;
            case 2:
                setRing(holder.ring, R.drawable.second);
                break;
            case 3:
                setRing(holder.ring, R.drawable.third);
                Log.d("tag", "third place set");
                break;
            default:
                holder.ring.setVisibility(View.INVISIBLE);
        }
        holder.scounter_out.setText(String.format(Locale.ENGLISH, "%d", thisData.out_symbols));
        holder.mcounter_out.setText(String.format(Locale.ENGLISH, "%d", thisData.out));
        holder.scounter_in.setText(String.format(Locale.ENGLISH, "%d", thisData.symbols - thisData.out_symbols));
        holder.mcounter_in.setText(String.format(Locale.ENGLISH, "%d", thisData.messages - thisData.out));
        holder.acounter.setText(String.format(Locale.ENGLISH, "%d", thisData.audios + thisData.videos
                + thisData.walls + thisData.pictures));
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

    private void setRing(ImageView view, int resource) {
        view.setVisibility(View.VISIBLE);
        view.setImageBitmap(BitmapFactory.decodeResource(context.getResources(), resource));
    }

    private String getShortName(Easies.DIALOG_TYPE type, String name) {
        switch (type) {
            case CHAT:
                return context.getString(R.string.other_stat);
            case COMMUNITY:
                return name;
            case USER:
                return name.split("\\W+")[0];
            default:
                return "Null type";
        }
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
        public TextView acounter;
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
            acounter = layout.findViewById(R.id.attachments_count);
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
