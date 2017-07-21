package com.vladsaif.vkmessagestat;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.ViewHolder> {

    private ArrayList<DialogData> data;

    public DialogsAdapter() {
        // TODO fetch data from database
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

        return new DialogsAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(DialogsAdapter.ViewHolder holder, int position) {
        holder.avatar.setImageBitmap(data.get(position).getAvatar());
        holder.title.setText(data.get(position).getName());
        holder.mcounter.setText(data.get(position).getMessages());
        holder.scounter.setText(data.get(position).getSymbols());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

}
