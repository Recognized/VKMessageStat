package com.vladsaif.vkmessagestat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.utils.Strings;

/**
 * An activity representing a single Dialog detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link MainPage}.
 */
public class DialogDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_detail);
        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle arguments = new Bundle();
            int dialog_id = getIntent().getIntExtra(Strings.dialog_id, 0);
            if (actionBar != null) {
                actionBar.setTitle(DialogsAdapter.data.get(dialog_id).name);
            }
            arguments.putInt(Strings.dialog_id, dialog_id);
            DialogDetailFragment fragment = new DialogDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.dialog_detail_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            navigateUpTo(new Intent(this, MainPage.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
