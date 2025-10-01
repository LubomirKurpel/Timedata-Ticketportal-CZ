package com.timedata.ticketportal;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.common.apiutil.util.SDKUtil;
import com.google.android.material.snackbar.Snackbar;
import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.classes.timedataCoreFunctions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class settingsPage extends AppCompatActivity {

	private TextView showTime;
	private ProgressDialog pDialog;
	public static final int progress_bar_type = 0;

	private EditText deviceIdEditText;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Initialize logging api
		timedataApi.initialize(this);

		// Immersive mode
		int flags = timedataCoreFunctions.flags;
		getWindow().getDecorView().setSystemUiVisibility(flags);

		final View decorView = getWindow().getDecorView();

		// get version
		PackageManager pm = getApplicationContext().getPackageManager();
		String pkgName = getApplicationContext().getPackageName();
		PackageInfo pkgInfo = null;
		try {
			pkgInfo = pm.getPackageInfo(pkgName, 0);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		String ver = pkgInfo.versionName;

		decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
					decorView.setSystemUiVisibility(flags);
				}
			}
		});

		// Set view
		setContentView(R.layout.settings_page);

		// Init SDK
		SDKUtil.getInstance(this).initSDK();

		// Show time in header
		showTime = findViewById(R.id.showTime);

		Handler handler2 = new Handler();
		handler2.postDelayed(new Runnable() {
			@SuppressLint("SetTextI18n")
			@Override
			public void run() {
				handler2.postDelayed(this, 1000);
				// Date
				Date now = new Date();
				TimeZone.setDefault(TimeZone.getTimeZone("Europe/Bratislava"));
				SimpleDateFormat formatMinutes = new SimpleDateFormat("mm");
				String getMinutes = formatMinutes.format(now);
				SimpleDateFormat formatHours = new SimpleDateFormat("HH");
				String getHours = formatHours.format(now);
				SimpleDateFormat formatSeconds = new SimpleDateFormat("ss");
				String getSeconds = formatSeconds.format(now);
				showTime.setText(getHours + ":" + getMinutes + ":" + getSeconds);
			}
		}, 1000);




		// Current App Version
		TextView currentVersion = findViewById(R.id.current_version_value);
		currentVersion.setText(ver);



		// Back to app button
		TextView backToAppButton = findViewById(R.id.spat_do_appky);
		backToAppButton.setOnClickListener(v -> {
			//startActivity(new Intent(settingsPage.this, MainActivity.class));
			finish(); // This will close the current activity
		});


		Button selectLauncherButton = findViewById(R.id.select_launcher_button);
		selectLauncherButton.setOnClickListener(v -> {
			startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
			finish(); // This will close the current activity
		});


		selectLauncherButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
				startActivity(intent);
				finish();
			}
		});

		// Settings set if exists
		deviceIdEditText = findViewById(R.id.device_id);

		// Load saved device ID if it exists
		loadDeviceId();

		// dropdown dnu von
		Spinner spinner = findViewById(R.id.spinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this,
				R.array.dropdown_values,
				R.layout.spinner_item
		);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinner.setAdapter(adapter);

		SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();

		String savedValue = sharedPreferences.getString("device_type", "Dnu");
		int spinnerPosition = adapter.getPosition(savedValue);
		spinner.setSelection(spinnerPosition);

		// save type
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String selectedValue = parent.getItemAtPosition(position).toString();
				editor.putString("device_type", selectedValue);
				editor.apply();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// Do nothing
			}
		});

	}

	// Method to save the device ID when the button is clicked
	public void saveDeviceId(View view) {
		String deviceId = deviceIdEditText.getText().toString();

		if (!deviceId.isEmpty()) {
			SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("device_id", deviceId);
			editor.apply();

			Toast.makeText(this, "Device ID saved", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "Device ID cannot be empty", Toast.LENGTH_SHORT).show();
		}

	}

	// Method to load the device ID from SharedPreferences
	private void loadDeviceId() {
		SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
		String deviceId = sharedPreferences.getString("device_id", "- Unset -");
		deviceIdEditText.setText(deviceId);
	}

	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	private void downloadWithProgress(Context context, String filename, String fileExtension, String url) {
		Log.i("url", url);

		// Delete file if it exists
		new File("/storage/emulated/0/Android/data/com.timedata.app/files/Download/" + filename + fileExtension).delete();

		// Create new file
		DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Uri uri = Uri.parse(url);
		DownloadManager.Request request = new DownloadManager.Request(uri);
		request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
		request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename + fileExtension);

		// Show progress dialog
		pDialog = new ProgressDialog(settingsPage.this);
		pDialog.setMessage("Downloading application...");
		pDialog.setIndeterminate(false);
		pDialog.setMax(100);
		pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pDialog.setCancelable(false);
		pDialog.show();

		final long downloadId = downloadManager.enqueue(request);

		// Monitor the progress
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				DownloadManager.Query query = new DownloadManager.Query();
				query.setFilterById(downloadId);
				try (Cursor cursor = downloadManager.query(query)) {
					if (cursor != null && cursor.moveToFirst()) {
						int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
						int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
						if (bytesTotal > 0) {
							int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
							pDialog.setProgress(progress);
						}
					}
				}
				if (pDialog.getProgress() < 100) {
					handler.postDelayed(this, 1000);
				} else {
					pDialog.dismiss();

					// Open the downloaded APK file for installation
					File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename + fileExtension);
					Uri apkUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", file);

					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
					intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(intent);
					//finish();
				}
			}
		}, 1000);
	}
}
