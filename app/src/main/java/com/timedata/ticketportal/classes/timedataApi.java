package com.timedata.ticketportal.classes;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import okhttp3.MediaType;
import okhttp3.RequestBody;

import org.json.JSONException;
import org.json.JSONObject;

public class timedataApi {
    static OkHttpClient client;

    public static String logURL = "https://timedata.grafdev.sk/api/log/send";

    public static String responseData;

    /*
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    */

    public static final MediaType Text
            = MediaType.parse("text/html; charset=utf-8");

    private static Context appContext;

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void sendLogData(String tag, String log) {

        client = new OkHttpClient();

        // Log.d(tag, log);
        String device_id = "TicketPortal CZ";

        String datetime = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

        SharedPreferences sharedPreferences = appContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
        String imei = sharedPreferences.getString("device_id", "- Unset -");

        JSONObject json = new JSONObject();

        try {
            json.put("tag", tag);
            json.put("log", log);
            json.put("datetime", datetime);
            json.put("device_id", device_id);
            json.put("imei", imei);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Log.d(tag, json.toString());

        // RequestBody params = RequestBody.create(json, Text);
        RequestBody params = RequestBody.create(json.toString(), Text);

        Request request = new Request.Builder()
                .url(logURL)
                .header("contentType", "application/json")
                .post(params)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    responseData = response.body().string();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
