package com.vladsaif.vkmessagestat.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.utils.Strings;

public class DialogDetailFragment extends Fragment {
    private DialogData currentDialog;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DialogDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(Strings.dialog_id)) {
            currentDialog = DialogsAdapter.data.get(getArguments().getInt(Strings.dialog_id));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_detail, container, false);

        if (currentDialog != null) {
            // TODO
        }

        return rootView;
    }
}
