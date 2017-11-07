package com.adsamcik.signalcollector.activities;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.adsamcik.signalcollector.R;
import com.adsamcik.signalcollector.adapters.TableAdapter;
import com.adsamcik.signalcollector.data.UploadStats;
import com.adsamcik.signalcollector.enums.AppendBehavior;
import com.adsamcik.signalcollector.file.DataStore;
import com.adsamcik.signalcollector.utility.Assist;
import com.adsamcik.signalcollector.utility.Table;
import com.google.gson.Gson;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

import static com.adsamcik.signalcollector.utility.Constants.MINUTE_IN_MILLISECONDS;

public class UploadReportsActivity extends DetailActivity {

	/**
	 * Function for generating table for upload stats
	 *
	 * @param uploadStat upload stat
	 * @param context    context
	 * @param title      title, if null is replaced with upload time
	 * @return table
	 */
	public static Table GenerateTableForUploadStat(@NonNull UploadStats uploadStat, @NonNull Context context, @Nullable String title, @AppendBehavior int appendBehavior) {
		Resources resources = context.getResources();
		Table t = new Table(9, false, 16, appendBehavior);


		if (title == null) {
			DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
			DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
			Date dateTime = new Date(uploadStat.time);
			t.setTitle(dateFormat.format(dateTime) + " " + timeFormat.format(dateTime));
		} else
			t.setTitle(title);

		t.addData(resources.getString(R.string.recent_upload_size), Assist.humanReadableByteCount(uploadStat.uploadSize, true));
		t.addData(resources.getString(R.string.recent_upload_collections), String.valueOf(uploadStat.collections));
		t.addData(resources.getString(R.string.recent_upload_locations_new), String.valueOf(uploadStat.newLocations));
		t.addData(resources.getString(R.string.recent_upload_wifi), String.valueOf(uploadStat.wifi));
		t.addData(resources.getString(R.string.recent_upload_wifi_new), String.valueOf(uploadStat.newWifi));
		t.addData(resources.getString(R.string.recent_upload_cell), String.valueOf(uploadStat.cell));
		t.addData(resources.getString(R.string.recent_upload_cell_new), String.valueOf(uploadStat.newCell));
		t.addData(resources.getString(R.string.recent_upload_noise), String.valueOf(uploadStat.noiseCollections));
		t.addData(resources.getString(R.string.recent_upload_noise_new), String.valueOf(uploadStat.newNoiseLocations));
		return t;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.recent_uploads);

		UploadStats[] recent = new Gson().fromJson(DataStore.loadAppendableJsonArray(this, DataStore.RECENT_UPLOADS_FILE), UploadStats[].class);
		Arrays.sort(recent, (uploadStats, t1) -> (int) ((t1.time - uploadStats.time) / MINUTE_IN_MILLISECONDS));
		if (recent.length > 0) {
			LinearLayout parent = createContentParent(false);
			ListView listView = new ListView(this);

			listView.setDivider(null);
			listView.setDividerHeight(0);
			listView.setSelector(android.R.color.transparent);
			parent.addView(listView);

			TableAdapter adapter = new TableAdapter(this, 16);
			listView.setAdapter(adapter);

			for (UploadStats s : recent)
				if (s != null)
					adapter.add(GenerateTableForUploadStat(s, this, null, AppendBehavior.Any));

		}


		//if (recent.size() > 10)
		//	DataStore.saveString(DataStore.RECENT_UPLOADS_FILE, new Gson().toJson(recent.subList(recent.size() - 11, recent.size() - 1)));
	}
}
