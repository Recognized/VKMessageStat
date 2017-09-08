package com.vladsaif.vkmessagestat.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.vladsaif.vkmessagestat.R;
import com.vladsaif.vkmessagestat.adapters.DialogsAdapter;
import com.vladsaif.vkmessagestat.charts.ChartConfigurator;
import com.vladsaif.vkmessagestat.charts.MessageDataSetAccum;
import com.vladsaif.vkmessagestat.db.DialogData;
import com.vladsaif.vkmessagestat.utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DialogDetailFragment extends Fragment {
    private DialogData currentDialog;
    private ImageView avatar;
    private Boolean wideScreenMode;
    private Context context;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public DialogDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = (getContext() != null ? getContext() : getActivity().getApplicationContext());
        if (getArguments().containsKey(Strings.dialog_id)) {
            currentDialog = DialogsAdapter.getData(context).get(getArguments().getInt(Strings.dialog_id));
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootViewRoot = inflater.inflate(R.layout.dialog_detail, container, false);
        if (getActivity().findViewById(R.id.dialogs) != null) {
            inflater.inflate(R.layout.fragment_toolbar, (ViewGroup) rootViewRoot, true);
            Toolbar toolbar = rootViewRoot.findViewById(R.id.toolbar);
            ImageView avatar = toolbar.findViewById(R.id.avatar);
            CacheFile.setImage(DialogsAdapter.getData(context).get(currentDialog.dialog_id),
                    new SetImageSimple(avatar, context));
            TextView name = toolbar.findViewById(R.id.title);
            name.setText(DialogsAdapter.getData(context).get(currentDialog.dialog_id).name);
            toolbar.setTitle("");
        }
        final View rootView = inflater.inflate(R.layout.dialog_detail_scroll, (ViewGroup) rootViewRoot, true);

        TextView your_messages = rootView.findViewById(R.id.your_messages);
        TextView your_symbols = rootView.findViewById(R.id.your_symbols);
        TextView your_photos = rootView.findViewById(R.id.your_photos);
        TextView your_audios = rootView.findViewById(R.id.your_audios);
        TextView your_videos = rootView.findViewById(R.id.your_videos);
        TextView your_posts = rootView.findViewById(R.id.your_posts);
        TextView your_other = rootView.findViewById(R.id.your_other);

        TextView other_name = rootView.findViewById(R.id.other_name);
        other_name.setText(Easies.getShortName(currentDialog.type, currentDialog.name));

        TextView other_messages = rootView.findViewById(R.id.other_messages);
        TextView other_symbols = rootView.findViewById(R.id.other_symbols);
        TextView other_photos = rootView.findViewById(R.id.other_photos);
        TextView other_audios = rootView.findViewById(R.id.other_audios);
        TextView other_videos = rootView.findViewById(R.id.other_videos);
        TextView other_posts = rootView.findViewById(R.id.other_posts);
        TextView other_other = rootView.findViewById(R.id.other_other);

        your_messages.setText(String.format(Locale.ENGLISH, "%d", currentDialog.out));
        your_symbols.setText(String.format(Locale.ENGLISH, "%d", currentDialog.out_symbols));
        your_photos.setText(String.format(Locale.ENGLISH, "%d", currentDialog.pictures));
        your_audios.setText(String.format(Locale.ENGLISH, "%d", currentDialog.audios));
        your_videos.setText(String.format(Locale.ENGLISH, "%d", currentDialog.videos));
        your_posts.setText(String.format(Locale.ENGLISH, "%d", currentDialog.walls));
        your_other.setText(String.format(Locale.ENGLISH, "%d", currentDialog.stickers +
                currentDialog.link_attachms + currentDialog.gifts + currentDialog.docs));

        other_messages.setText(String.format(Locale.ENGLISH, "%d", currentDialog.messages - currentDialog.out));
        other_symbols.setText(String.format(Locale.ENGLISH, "%d", currentDialog.symbols - currentDialog.out_symbols));
        other_photos.setText(String.format(Locale.ENGLISH, "%d", currentDialog.other_pictures));
        other_audios.setText(String.format(Locale.ENGLISH, "%d", currentDialog.other_audios));
        other_videos.setText(String.format(Locale.ENGLISH, "%d", currentDialog.other_videos));
        other_posts.setText(String.format(Locale.ENGLISH, "%d", currentDialog.other_walls));
        other_other.setText(String.format(Locale.ENGLISH, "%d", currentDialog.other_stickers +
                currentDialog.other_link_attachms + currentDialog.other_gifts + currentDialog.other_docs));

        // All things with first bar
        if (currentDialog.type != Easies.DIALOG_TYPE.CHAT) {
            rootView.findViewById(R.id.pie_chart_container).setVisibility(View.GONE);
            rootView.findViewById(R.id.horizontal_bar_container).setVisibility(View.VISIBLE);
            HorizontalBarChart in_out_bar = rootView.findViewById(R.id.in_out_bar);
            TextView left_percent = rootView.findViewById(R.id.left_percent);
            TextView right_percent = rootView.findViewById(R.id.right_percent);
            float you = ((int) (((float) currentDialog.out) / currentDialog.messages * 1000) / 10f);
            PercentFormatter percentFormatter = new PercentFormatter();
            left_percent.setText(percentFormatter.getFormattedValue(you, null));
            right_percent.setText(percentFormatter.getFormattedValue(100 - you, null));
            List<BarEntry> entries = new ArrayList<>();
            entries.add(new BarEntry(0, new float[]{you, 100 - you}));
            BarDataSet barDataSet = new BarDataSet(entries, "");
            ChartConfigurator.makeMessageBarChart(in_out_bar, barDataSet, getMyColors(), context);
        } else {
            PieChart pieChart = rootView.findViewById(R.id.pie_chart);
            rootView.findViewById(R.id.pie_chart_container).setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.horizontal_bar_container).setVisibility(View.GONE);
            ChartConfigurator.makePieChartChatters(pieChart, currentDialog, DialogsAdapter.getGlobalData(context),
                    getMaterialColors(), context);
            ViewGroup.LayoutParams layoutParams = pieChart.getLayoutParams();
            Pair<Integer, Integer> dim = Easies.getScreenDimensions(getActivity());
            if (dim.first > dim.second) {
                float new_width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dim.second - 100,
                        getResources().getDisplayMetrics());
                layoutParams.height = (int) new_width;
            } else {
                float new_width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dim.first - 20,
                        getResources().getDisplayMetrics());
                layoutParams.height = (int) new_width;
            }
            pieChart.setLayoutParams(layoutParams);
        }

        // end first bar
        final FrameLayout frameLayout = rootView.findViewById(R.id.accum_chart_container);
        LayoutInflater layoutInflater = getLayoutInflater(null);
        layoutInflater.inflate(R.layout.progress, frameLayout, true);
        final LineChart day_activity = rootView.findViewById(R.id.second_chart);
        day_activity.setVisibility(View.INVISIBLE);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                MessageDataSetAccum dataSetAccum = new MessageDataSetAccum("Количество сообщений",
                        currentDialog.dialog_id, context);
                ChartConfigurator.makeSimpleLineChart(day_activity,
                        dataSetAccum, dataSetAccum.firstMessageTime, dataSetAccum.lastMessage, context);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                day_activity.setVisibility(View.VISIBLE);
                frameLayout.findViewById(R.id.progress).setVisibility(View.GONE);
            }
        }.execute();

        final FrameLayout themes_container = rootView.findViewById(R.id.themes_chart_container);
        if (currentDialog.themesEnabled) {
            layoutInflater.inflate(R.layout.progress, themes_container, true);
            final HorizontalBarChart themes_chart = rootView.findViewById(R.id.themes_chart);
            themes_chart.setVisibility(View.INVISIBLE);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    ChartConfigurator.makeThemesChart(themes_chart, currentDialog, context);
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    themes_container.findViewById(R.id.progress).setVisibility(View.GONE);
                    themes_chart.setVisibility(View.VISIBLE);
                    String[] labels = themes_chart.getData().getDataSetByIndex(0).getStackLabels();
                    int positive = currentDialog.getPositiveRelative();
                    int negative = currentDialog.getNegativeRelative();
                    TextView pos = themes_container.findViewById(R.id.positive);
                    TextView neg = themes_container.findViewById(R.id.negative);
                    String[] posLabels = new String[positive];
                    String[] negLabels = new String[negative];
                    System.arraycopy(labels, 0, posLabels, 0, positive);
                    System.arraycopy(labels, positive, negLabels, 0, negative);
                    StringBuilder textPos = new StringBuilder();
                    if (positive > 0) textPos = new StringBuilder(posLabels[0]);
                    for (int i = 1; i < positive; ++i) {
                        textPos.append('\n').append(posLabels[i]);
                    }
                    pos.setText(textPos);
                    StringBuilder textNeg = new StringBuilder();
                    for (int i = 0; i < positive; ++i) {
                        textNeg.append('\n');
                    }
                    if (negative > 0) textNeg.append(negLabels[0]);
                    for (int i = 1; i < negative; ++i) {
                        textNeg.append('\n').append(negLabels[i]);
                    }
                    neg.setText(textNeg);
                }
            }.execute();
        } else {
            themes_container.setVisibility(View.GONE);
        }


        return rootView;
    }

    private int[] getMyColors() {
        return new int[]{ContextCompat.getColor(context, R.color.in_out_first),
                ContextCompat.getColor(context, R.color.in_out_second)};
    }

    private int[] getMaterialColors() {
        return new int[]{ContextCompat.getColor(context, R.color.material4),
                ContextCompat.getColor(context, R.color.material3),
                ContextCompat.getColor(context, R.color.material1),
                ContextCompat.getColor(context, R.color.material2),
                ContextCompat.getColor(context, R.color.material5),
                ContextCompat.getColor(context, R.color.material6),
                ContextCompat.getColor(context, R.color.material7),
                ContextCompat.getColor(context, R.color.material8),
                ContextCompat.getColor(context, R.color.material9),
                ContextCompat.getColor(context, R.color.material10),
        };
    }
}
