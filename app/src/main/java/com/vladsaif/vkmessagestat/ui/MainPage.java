package com.vladsaif.vkmessagestat.ui;

import android.animation.LayoutTransition;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vk.sdk.VKAccessToken;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.services.MessagesCollectorNew;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Pair;
import com.vladsaif.vkmessagestat.utils.Strings;

public class MainPage extends AppCompatActivity {
    private static final String LOG_TAG = MainPage.class.getSimpleName();
    private VKAccessToken token;
    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private ProgressBar mProgress;
    private int currentAdapterOrder;
    private DialogsAdapter currentAdapter;
    private MenuItem order_desc;
    private MenuItem order_asc;
    private MenuItem order_time_desc;
    private MenuItem order_time_asc;
    public boolean mTwoPane;
    public FrameLayout detailContainer;
    private boolean refreshing = false;
    private boolean needToRefresh = false;
    private Handler refresher;
    private TextView refreshBar;
    private boolean wasRunning;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pair<Integer, Integer> dim = Easies.getScreenDimensions(this);
        if (dim.first >= 900) setContentView(R.layout.activity_main_page_wide);
        else setContentView(R.layout.activity_main_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        toolbar.setTitle("Диалоги");
        setSupportActionBar(toolbar);
        refreshBar = (TextView) findViewById(R.id.refreshing);
        wasRunning = MessagesCollectorNew.serviceRunning;
        if (MessagesCollectorNew.serviceRunning) {
            refreshBar.setVisibility(View.VISIBLE);
        }
        detailContainer = (FrameLayout) findViewById(R.id.dialog_detail_container);
        if (detailContainer != null) {
            mTwoPane = true;
            ViewGroup pane = detailContainer;
            LayoutTransition layoutTransition = pane.getLayoutTransition();
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        }
        mProgress = (ProgressBar) findViewById(R.id.loadingRecycler);
        mRecyclerView = (RecyclerView) findViewById(R.id.dialogs);
        token = VKAccessToken.tokenFromSharedPreferences(getApplication(), Strings.access_token);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        ViewGroup group = (ViewGroup) findViewById(R.id.frame);
        LayoutTransition layoutTransition = group.getLayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);

        refresher = new Handler(getMainLooper());
        refresher.post(refresh);

        SharedPreferences sPref = getSharedPreferences("settings", MODE_PRIVATE);
        currentAdapterOrder = sPref.getInt("adapter_order", DialogsAdapter.ORDER_TIME_DESC);
        new SetDialogAdapter().execute(currentAdapterOrder);
        styleMenuButton();
    }

    private class SetDialogAdapter extends AsyncTask<Integer, Void, DialogsAdapter> {

        @Override
        protected DialogsAdapter doInBackground(Integer... objects) {
            currentAdapter = new DialogsAdapter(MainPage.this, objects[0], mRecyclerView);
            return currentAdapter;
        }

        @Override
        protected void onPostExecute(DialogsAdapter dialogsAdapter) {
            mProgress.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAdapter(dialogsAdapter);
        }
    }

    private Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (needToRefresh || (wasRunning && !MessagesCollectorNew.serviceRunning)) {
                needToRefresh = false;
                refreshBar.setVisibility(View.GONE);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        SharedPreferences sPref = getSharedPreferences("settings", MODE_PRIVATE);
                        currentAdapterOrder = sPref.getInt("adapter_order", DialogsAdapter.ORDER_TIME_DESC);
                        currentAdapter.reloadData(currentAdapterOrder);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        mProgress.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                        currentAdapter.notifyDataSetChanged();
                    }
                }.execute();
                refreshing = false;
            }
            refresher.postDelayed(this, 500);
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        public MessagesCollectorNew.Progress binder;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (MessagesCollectorNew.Progress) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (binder != null) {
                needToRefresh = binder.isRefreshingFinished();
            }
            if (connection != null) unbindService(connection);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main_page, menu);
        order_asc = menu.findItem(R.id.order_asc);
        order_desc = menu.findItem(R.id.order_desc);
        order_time_asc = menu.findItem(R.id.order_time_asc);
        order_time_desc = menu.findItem(R.id.order_time_desc);
        switch (currentAdapterOrder) {
            case DialogsAdapter.ORDER_ASC:
                order_asc.setEnabled(false);
                break;
            case DialogsAdapter.ORDER_DESC:
                order_desc.setEnabled(false);
                break;
            case DialogsAdapter.ORDER_TIME_ASC:
                order_time_asc.setEnabled(false);
                break;
            case DialogsAdapter.ORDER_TIME_DESC:
                order_time_desc.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (currentAdapter == null && item.getItemId() != R.id.settings) return super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.settings:
                // TODO
                // User chose the "Settings" item, show the app settings UI...
                return true;

            case R.id.order_asc:
                currentAdapter.sortData(DialogsAdapter.ORDER_ASC);
                changeSortState(R.id.order_asc);
                closeOptionsMenu();
                return true;

            case R.id.order_desc:
                currentAdapter.sortData(DialogsAdapter.ORDER_DESC);
                changeSortState(R.id.order_desc);
                closeOptionsMenu();
                return true;

            case R.id.order_time_asc:
                currentAdapter.sortData(DialogsAdapter.ORDER_TIME_ASC);
                changeSortState(R.id.order_time_asc);
                closeOptionsMenu();
                return true;

            case R.id.order_time_desc:
                currentAdapter.sortData(DialogsAdapter.ORDER_TIME_DESC);
                changeSortState(R.id.order_time_desc);
                closeOptionsMenu();
                return true;

            case R.id.upwards:
                mRecyclerView.getLayoutManager().scrollToPosition(0);
                mRecyclerView.stopScroll();
                closeOptionsMenu();
                return true;

            case R.id.refresh:
                if (!refreshing) {
                    refreshing = true;
                    if (!MessagesCollectorNew.serviceRunning) {
                        refreshBar.setVisibility(View.VISIBLE);
                        Intent service = new Intent(this, MessagesCollectorNew.class);
                        service.putExtra(Strings.commandType, Strings.commandRefresh);
                        startService(service);
                    }
                    bindService(new Intent(this, MessagesCollectorNew.class), connection, 0);
                }
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void changeSortState(int disabled_item) {
        order_desc.setEnabled(true);
        order_asc.setEnabled(true);
        order_time_asc.setEnabled(true);
        order_time_desc.setEnabled(true);
        switch (disabled_item) {
            case R.id.order_asc:
                order_asc.setEnabled(false);
                break;
            case R.id.order_desc:
                order_desc.setEnabled(false);
                break;
            case R.id.order_time_asc:
                order_time_asc.setEnabled(false);
                break;
            case R.id.order_time_desc:
                order_time_desc.setEnabled(false);
        }
    }

    private void styleMenuButton() {
        // Find the menu item you want to style
        View view = findViewById(R.id.upwards);

        // Cast to a TextView instance if the menu item was found
        if (view != null && view instanceof TextView) {
            ((TextView) view).setTextColor(Color.WHITE); // Make text colour blue // Increase font size
        }
    }
}



