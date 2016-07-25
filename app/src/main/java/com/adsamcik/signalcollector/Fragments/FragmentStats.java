package com.adsamcik.signalcollector.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.JsonReader;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.adsamcik.signalcollector.DataStore;
import com.adsamcik.signalcollector.Network;
import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.Table;
import com.adsamcik.signalcollector.data.Stat;
import com.adsamcik.signalcollector.data.StatData;
import com.adsamcik.signalcollector.interfaces.ITabFragment;
import com.adsamcik.signalcollector.play.PlayController;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.cache.Resource;

public class FragmentStats extends Fragment implements ITabFragment {
	private static final String GENERAL_STAT_FILE = "general_stats_cache_file";
	private static final String USER_STAT_FILE = "user_stats_cache_file";
	private static int lastIndex = -1;
	private static long lastRequest = 0;
	private final AsyncHttpClient client = new AsyncHttpClient();
	private View v;

	//todo add user stats
	//todo add last day stats

	//todo Improve stats updating
	private final AsyncHttpResponseHandler generalStatsResponseHandler = new AsyncHttpResponseHandler() {
		@Override
		public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
			if (responseBody != null && responseBody.length > 0)
				try {
					String data = new String(responseBody);
					DataStore.saveString(GENERAL_STAT_FILE, data);
					GenerateStatsTable(readJsonStream(new ByteArrayInputStream(responseBody)));
				} catch (IOException e) {
					Log.e("Error", e.getMessage());
				}
		}

		@Override
		public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {

		}
	};

	private TableLayout GenerateTableWithHeader(Context c, String title) {
		TableLayout table = new TableLayout(c);
		Resources r = getResources();
		int hPadding = (int) r.getDimension(R.dimen.activity_horizontal_margin);
		table.setPadding(hPadding, 30, hPadding, 30);
		table.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.cardBackground));
		table.setElevation(r.getDimension(R.dimen.main_card_elevation));

		TextView label = new TextView(c);
		label.setTextSize(18);
		label.setText(title);
		label.setTypeface(null, Typeface.BOLD);
		label.setGravity(Gravity.CENTER);
		label.setPadding(0, 0, 0, 30);
		table.addView(label);
		return table;
	}

	private TableRow GenerateRow(Context c, int index, boolean showIndex, String id, String value) {
		TableRow row = new TableRow(c);
		row.setPadding(0, 0, 0, 20);

		if (showIndex) {
			TextView rowNum = new TextView(c);
			rowNum.setText(String.format(Locale.UK, "%d", index));
			rowNum.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.5f));
			rowNum.setTextSize(15);
			row.addView(rowNum);
		}

		TextView textId = new TextView(c);
		textId.setText(id);
		textId.setTextSize(15);
		textId.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 5f));
		row.addView(textId);

		TextView textValue = new TextView(c);
		textValue.setText(value);
		textValue.setTextSize(15);
		textValue.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
		textValue.setGravity(Gravity.END);
		row.addView(textValue);
		return row;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_stats, container, false);

		//todo show notification when offline to let know that the stats might be outdated
		v = view;
		long time = System.currentTimeMillis();
		time -= time % 600000;
		time += 120000;
		long diff = time - lastRequest;

		//todo show local device stats
		if (diff > 600000) {
			client.get(Network.URL_STATS, null, generalStatsResponseHandler);
			lastRequest = time;
		} else {
			String data;
			if (DataStore.exists(GENERAL_STAT_FILE)) {
				data = DataStore.loadString(GENERAL_STAT_FILE);
				try {
					GenerateStatsTable(readJsonStream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
				} catch (IOException e) {
					Log.e("Error", e.getMessage());
				}
			}
		}
		return view;
	}

	private List<Stat> readJsonStream(InputStream in) throws IOException {
		try (JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"))) {
			return readStatDataArray(reader);
		}
	}

	private List<Stat> readStatDataArray(JsonReader reader) throws IOException {
		List<Stat> l = new ArrayList<>();
		reader.beginArray();
		while (reader.hasNext())
			l.add(readStat(reader));
		reader.endArray();
		return l;
	}

	private Stat readStat(JsonReader reader) throws IOException {
		String name = null, type = null, className;
		List<StatData> statData = null;
		boolean showPosition = false;

		reader.beginObject();
		while (reader.hasNext()) {
			className = reader.nextName();
			switch (className) {
				case "name":
					name = reader.nextString();
					break;
				case "type":
					type = reader.nextString();
					break;
				case "showPosition":
					showPosition = reader.nextBoolean();
					break;
				case "data":
					statData = readData(reader);
					break;
				default:
					reader.skipValue();
					break;
			}
		}
		reader.endObject();
		return new Stat(name, type, showPosition, statData);
	}

	private List<StatData> readData(JsonReader reader) throws IOException {
		String id = null, value = null;

		List<StatData> data = new ArrayList<>();

		reader.beginArray();
		while (reader.hasNext()) {
			reader.beginObject();
			while (reader.hasNext()) {
				String name = reader.nextName();
				switch (name) {
					case "id":
						id = reader.nextString();
						break;
					case "value":
						value = reader.nextString();
						break;
					default:
						reader.skipValue();
				}
			}
			reader.endObject();
			data.add(new StatData(id, value));
		}
		reader.endArray();

		return data;
	}

	private void GenerateStatsTable(List<Stat> stats) {
		Context c = getContext();
		Resources r = getResources();
		int vPadding = (int) r.getDimension(R.dimen.activity_vertical_margin);
		RelativeLayout ll = (RelativeLayout) v.findViewById(R.id.statsLayout);
		for (int i = 0; i < stats.size(); i++) {
			Stat s = stats.get(i);
			Table table = new Table(c, s.statData.size(), s.showPosition);
			table.setTitle(s.name);
			for (int y = 0; y < s.statData.size(); y++) {
				StatData sd = s.statData.get(y);
				table.addRow();
				table.addData(sd.id, sd.value);
			}

			RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT,
					RelativeLayout.LayoutParams.WRAP_CONTENT);

			lp.topMargin = vPadding;

			if (lastIndex >= 0) {
				lp.addRule(RelativeLayout.BELOW, lastIndex);
				//Log.d("test", "id " + ll.getChildAt(i - 1).getId());
			}

			lastIndex = View.generateViewId();
			table.getLayout().setId(lastIndex);
			//table.setLayoutParams(lp);
			ll.addView(table.getLayout(), lp);
			//previous = table;
		}
	}

	@Override
	public boolean onEnter(Activity activity, FloatingActionButton fabOne, FloatingActionButton fabTwo) {
		//todo check if up to date
		fabOne.hide();
		fabTwo.hide();
		return true;
	}

	@Override
	public void onLeave() {

	}
}

