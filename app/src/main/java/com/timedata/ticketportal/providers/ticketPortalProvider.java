package com.timedata.ticketportal.providers;

import static android.app.PendingIntent.getActivity;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.classes.timedataCoreFunctions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import okhttp3.FormBody;

public class ticketPortalProvider extends Activity  {

    OkHttpClient client;
    private Context mContext;
    public static String httpStatusCodeForRedirect = "empty";

    /*
    * private String login_name = "timedata";
    private String login_pass = "123456";
    * */

    // CZ:
    private String login_name = "turniket126";
    private String login_pass = "Rysnan-povhix-4fazma";

    // Skalica:
    // private String login_name = "skalturn";
    // private String login_pass = "Sk4lTurn";

    // check QR code
    public void checkQRCodeStatus(String QR_Code, Context context) throws JSONException, IOException {

        Log.d("REQUEST QR_Code", QR_Code);
        timedataApi.sendLogData("TICKETPORTAL API - checkQRCodeStatus", QR_Code);

        ////////////////////////////// REQUEST 1 - GET SESSION-ID

        client = new OkHttpClient();
        Request request = new Request.Builder().url("http://tpcheck.ticketportal.cz/LogMeIn.ashx?meno="+login_name+"&heslo=" + login_pass).post(new FormBody.Builder().build()).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // red screen
                // @dev: added advanced logging
                String errorMessage = e.getMessage();
                String stackTrace = Log.getStackTraceString(e);

                Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                timedataApi.sendLogData("api error: checkQRCodeStatus", "onFailure: " + errorMessage + "\n" + stackTrace);

                httpStatusCodeForRedirect = "red";
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String sessionID = jsonObject.getString("sessionID");

                    if (sessionID != "") {
                        Log.d("REQUEST session ID", sessionID);
                        timedataApi.sendLogData("REQUEST 4 - session ID checkQRCodeStatus", sessionID);

                            ////////////////////////////// REQUEST 2 - // ZOZNAM PREDSTAVENI -- // GET ID_PREDSTAVENIA

                            Request request2 = new Request.Builder().url("http://tpcheck.ticketportal.cz/GetPredstavenia.ashx?SessionID=" + sessionID).post(new FormBody.Builder().build()).build();
                            client.newCall(request2).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    // red screen
                                    // @dev: added advanced logging
                                    String errorMessage = e.getMessage();
                                    String stackTrace = Log.getStackTraceString(e);

                                    Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                                    Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                                    timedataApi.sendLogData("api error: checkQRCodeStatus", "onFailure 2: " + errorMessage + "\n" + stackTrace);

                                    httpStatusCodeForRedirect = "red";
                                }
                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                   // Log.i("REQUEST 2 - // ZOZNAM PREDSTAVENI", response.body().string());
                                   // {"status":"OK","predstavenia":[{"IDjavisko":0,"IDpodujatie":0,"IDpredstavenie":4314,"PlatnostDo":"2024-05-17 09:21:00","PlatnostOd":"2024-05-09 09:21:00","Zony":[{"ID":1,"Nazov":"#1"}],"javisko":"","nazov":"Test time data ","popis":"","sektor":"","zaciatok":"2024-05-09 10:00:00"}]}

                                    try {

                                        JSONObject jsonObjectPredstavenia = new JSONObject(response.body().string());
                                        String jsonStatusString = jsonObjectPredstavenia.getString("status");

                                        // status OK
                                        if (jsonStatusString.contains("OK")){
                                            Log.i("REQUEST 2 - status", jsonStatusString);
                                            Log.i("REQUEST 2 - predstavenia", jsonObjectPredstavenia.getJSONArray("predstavenia").toString());

                                            String IDpredstavenie = "";

                                            // foreach predstavenia
                                            JSONArray jsonPredstavenia = jsonObjectPredstavenia.getJSONArray("predstavenia");
                                            for (int i = 0; i < jsonPredstavenia.length(); i++) {
                                                JSONObject jsonPredstaveniaObject = jsonPredstavenia.getJSONObject(i);

                                                String PlatnostDo = jsonPredstaveniaObject.getString("PlatnostDo");
                                                String PlatnostOd = jsonPredstaveniaObject.getString("PlatnostOd");
                                                String IDpredstavenieGet = jsonPredstaveniaObject.getString("IDpredstavenie");

                                                // get timestamps
                                                Long timestampPlatnostDo = java.sql.Timestamp.valueOf( PlatnostDo).getTime() /1000;
                                                Long timestampPlatnostOd = java.sql.Timestamp.valueOf( PlatnostOd).getTime() /1000;
                                                Long actualTimestamp = System.currentTimeMillis()/1000;

                                                // if actual timestamp is between start&end timestamp
                                                if(actualTimestamp < timestampPlatnostDo && actualTimestamp > timestampPlatnostOd){
                                                    // set ID_predstavenia
                                                    IDpredstavenie = IDpredstavenieGet;
                                                }

                                            }

                                            ////////////////////////////// REQUEST 3 - // OVERENIE TICKETU
                                            if (!IDpredstavenie.equals("")) {
                                                Log.i("REQUEST 2 - ID_predstavenia", IDpredstavenie);
                                                timedataApi.sendLogData("REQUEST 2 - ID_predstavenia", IDpredstavenie);

                                                // Setup from GetPredstavenia
                                                String IDjavisko = "0";
                                                String IDpodujatie = "0";
                                                String IDzona = "0";
                                                String Kod = QR_Code;

                                                // create zony json
                                                JSONArray arrayz = new JSONArray();
                                                arrayz.put(IDzona);
                                                JSONObject zonyJsonObject = new JSONObject();
                                                zonyJsonObject.put("Zony",arrayz);

                                                // create Predstavenia json
                                                JSONArray array = new JSONArray();
                                                array.put(IDpredstavenie);
                                                JSONObject predstaveniaJsonObject = new JSONObject();
                                                predstaveniaJsonObject.put("Predstavenia",array);

                                                Request request3 = new Request.Builder().url("http://tpcheck.ticketportal.cz/OverMiestoN.ashx?SessionID=" + sessionID + "&TypKodu=0&Kod=" + Kod + "&Predstavenia=" + predstaveniaJsonObject + "&Zony=" + zonyJsonObject).post(new FormBody.Builder().build()).build();
                                                client.newCall(request3).enqueue(new Callback() {

                                                    @Override
                                                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                                        // red screen
                                                        // @dev: added advanced logging
                                                        String errorMessage = e.getMessage();
                                                        String stackTrace = Log.getStackTraceString(e);

                                                        Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                                                        Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                                                        timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse VALIDACIA onFailure: " + errorMessage + "\n" + stackTrace);

                                                        httpStatusCodeForRedirect = "red";
                                                    }
                                                    @Override
                                                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                                                        try {

                                                            JSONObject jsonObjectoverenieResponse = new JSONObject(response.body().string());
                                                            String status = jsonObjectoverenieResponse.getString("statusOvereniePopis");




                                                            SharedPreferences sharedPreferences = context.getSharedPreferences("AppPreferences", MODE_PRIVATE);
                                                            String sharedPrefDnuVon = sharedPreferences.getString("device_type", "Dnu");
                                                            Log.i("REQUEST 3 - // PRECHODOVY", "PRECHODOVY: " + sharedPrefDnuVon + " - STATUS: " + status);

                                                            // sharedPrefDnuVon = Von / Dnu


                                                            if(status.contains("OK") || status.contains("Je_vonku")){
                                                                Log.i("REQUEST 3 - // OVERENIE TICKETU", "SUPER " + status);
                                                                timedataApi.sendLogData("api: checkQRCodeStatus", "onResponse VALIDACIA OK green screen status:" + status);
                                                                // green screen
                                                                httpStatusCodeForRedirect = "green";
                                                                timedataCoreFunctions.greenLightOn(context);
                                                                timedataCoreFunctions.otvorTurniket(context);

                                                            } else if (status.contains("Je_vnutri")) {

                                                                ////////////////////// ak je prechodovy  - ak ano tak greenscreen
                                                                if(sharedPrefDnuVon.contains("Von")){
                                                                    // green
                                                                    Log.i("REQUEST 3 - // OVERENIE TICKETU", "SUPER " + status + " - PRECHODOVY");
                                                                    timedataApi.sendLogData("api: checkQRCodeStatus", "onResponse VALIDACIA OK green screen - PRECHODOVY");
                                                                    // green screen
                                                                    httpStatusCodeForRedirect = "green";
                                                                    timedataCoreFunctions.greenLightOn(context);
                                                                    timedataCoreFunctions.otvorTurniket(context);

                                                                }else{
                                                                    // red
                                                                    Log.i("REQUEST 3 - // OVERENIE TICKETU", "JE VNUTRI ALE TURNIKET NIEJE PRECHODOVY");
                                                                    // red screen
                                                                    timedataApi.sendLogData("api error: checkQRCodeStatus", "status: JE VNUTRI, ALE TURNIKET NIEJE PRECHODOVY");

                                                                    httpStatusCodeForRedirect = "red";
                                                                    timedataCoreFunctions.redLightOn(context);
                                                                }


                                                            }else{


                                                                Log.i("REQUEST 3 - // OVERENIE TICKETU", "TICKET NEBOL OVERENY " + status);
                                                                // red screen
                                                                timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse VALIDACIA status: " + status);

                                                                httpStatusCodeForRedirect = "red";
                                                                timedataCoreFunctions.redLightOn(context);
                                                            }

                                                        } catch (JSONException e) {
                                                            // red screen
                                                            timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse VALIDACIA JSONException");
                                                            httpStatusCodeForRedirect = "red";
                                                        }

                                                    }


                                                });


                                            }else{
                                                // red screen
                                                timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse ZOZNAM PREDSTAVENI empty");
                                                httpStatusCodeForRedirect = "red";
                                            }


                                        }else{
                                            Log.i("REQUEST 2 - status wrong", jsonStatusString);
                                            // red screen
                                            timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse ZOZNAM PREDSTAVENI status wrong");
                                            httpStatusCodeForRedirect = "red";
                                        }

                                    } catch (JSONException e) {
                                        Log.i("REQUEST 2 - ZOZNAM PREDSTAVENI ERROR", e.toString());
                                        // red screen
                                        timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse JSONException ZOZNAM PREDSTAVENI ERROR");
                                        httpStatusCodeForRedirect = "red";
                                    }
                                }
                            });



                    }else{
                        // red screen
                        timedataApi.sendLogData("api error: checkQRCodeStatus", "sessionID empty");
                        httpStatusCodeForRedirect = "red";
                    }

                } catch (JSONException e) {
                    // red screen
                    timedataApi.sendLogData("api error: checkQRCodeStatus", "onResponse JSONException");
                    httpStatusCodeForRedirect = "red";
                }
            }
        });

    }

    // validate QR code from 485 reader
    public void validateQRCode(String QR_Code, Context context) throws JSONException {

        Log.d("REQUEST 4 - Start", "Validation start " + QR_Code);

        timedataApi.sendLogData("TICKETPORTAL API - Validation start: ", QR_Code);

        ////////////////////////////// REQUEST 1 - GET SESSION-ID

        client = new OkHttpClient();
        Request request = new Request.Builder().url("http://tpcheck.ticketportal.cz/LogMeIn.ashx?meno="+login_name+"&heslo=" + login_pass).post(new FormBody.Builder().build()).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // red screen
                // @dev: added advanced logging
                String errorMessage = e.getMessage();
                String stackTrace = Log.getStackTraceString(e);

                Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                timedataApi.sendLogData("api error: validateQRCode", "onResponse VALIDACIA onFailure: " + errorMessage + "\n" + stackTrace);
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String sessionID = jsonObject.getString("sessionID");

                    if (sessionID != "") {
                        Log.d("REQUEST 4 - session ID", sessionID);
                        timedataApi.sendLogData("REQUEST 4 - session ID validateQRCode", sessionID);

                        ////////////////////////////// REQUEST 2 - // ZOZNAM PREDSTAVENI -- // GET ID_PREDSTAVENIA

                        Request request2 = new Request.Builder().url("http://tpcheck.ticketportal.cz/GetPredstavenia.ashx?SessionID=" + sessionID).post(new FormBody.Builder().build()).build();

                        client.newCall(request2).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                // red screen
                                // @dev: added advanced logging
                                String errorMessage = e.getMessage();
                                String stackTrace = Log.getStackTraceString(e);

                                Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                                Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                                timedataApi.sendLogData("api error: validateQRCode", "onFailure request2: " + errorMessage + "\n" + stackTrace);
                            }
                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                                try {

                                    JSONObject jsonObjectPredstavenia = new JSONObject(response.body().string());
                                    String jsonStatusString = jsonObjectPredstavenia.getString("status");

                                    // status OK
                                    if (jsonStatusString.contains("OK")){
                                        Log.i("REQUEST 4 - status", jsonStatusString);
                                        Log.i("REQUEST 4 - predstavenia", jsonObjectPredstavenia.getJSONArray("predstavenia").toString());

                                        timedataApi.sendLogData("REQUEST 4 - status", jsonStatusString);
                                        timedataApi.sendLogData("REQUEST 4 - predstavenia", jsonObjectPredstavenia.getJSONArray("predstavenia").toString());

                                        String IDpredstavenie = "";

                                        // foreach predstavenia
                                        JSONArray jsonPredstavenia = jsonObjectPredstavenia.getJSONArray("predstavenia");
                                        for (int i = 0; i < jsonPredstavenia.length(); i++) {
                                            JSONObject jsonPredstaveniaObject = jsonPredstavenia.getJSONObject(i);

                                            String PlatnostDo = jsonPredstaveniaObject.getString("PlatnostDo");
                                            String PlatnostOd = jsonPredstaveniaObject.getString("PlatnostOd");
                                            String IDpredstavenieGet = jsonPredstaveniaObject.getString("IDpredstavenie");

                                            // get timestamps
                                            Long timestampPlatnostDo = java.sql.Timestamp.valueOf( PlatnostDo).getTime() /1000;
                                            Long timestampPlatnostOd = java.sql.Timestamp.valueOf( PlatnostOd).getTime() /1000;
                                            Long actualTimestamp = System.currentTimeMillis()/1000;

                                            // if actual timestamp is between start&end timestamp
                                            if(actualTimestamp < timestampPlatnostDo && actualTimestamp > timestampPlatnostOd){
                                                // set ID_predstavenia
                                                IDpredstavenie = IDpredstavenieGet;
                                            }
                                        }

                                        ////////////////////////////// REQUEST 4 - // PREJDI TICKET
                                        if(!IDpredstavenie.equals("")){

                                            SimpleDateFormat currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                            String currentDateandTime = currentTime.format(new Date());

                                            JSONObject kodyDetail = new JSONObject();
                                            kodyDetail.put("IDpredstavenie", IDpredstavenie);


                                            SharedPreferences sharedPreferences = context.getSharedPreferences("AppPreferences", MODE_PRIVATE);
                                            String sharedPrefDnuVon = sharedPreferences.getString("device_type", "Dnu");
                                            timedataApi.sendLogData("REQUEST 4 - // PRECHODOVY VALIDACIA", sharedPrefDnuVon);
                                            Log.i("REQUEST 4 - // PRECHODOVY VALIDACIA", sharedPrefDnuVon);

                                            if(sharedPrefDnuVon.contains("Von")){
                                                ///////////// 2 ak je prechodovy obratit smer
                                                kodyDetail.put("Smer", 1); // 0 dnu, 1 von
                                            }else{
                                                kodyDetail.put("Smer", 0); // 0 dnu, 1 von
                                            }


                                            kodyDetail.put("TypKodu", 0);  // 0 = barkod, 1 = mifare
                                            kodyDetail.put("Kod", QR_Code);
                                            kodyDetail.put("DatumCas", currentDateandTime);


                                            JSONArray arrayK = new JSONArray();
                                            arrayK.put(kodyDetail);
                                            JSONObject kodyJsonObject = new JSONObject();
                                            kodyJsonObject.put("Kody",arrayK);


                                            // kodyJsonObject
                                            Request request4 = new Request.Builder().url("http://tpcheck.ticketportal.cz/AddPrechody.ashx?SessionID=" + sessionID + "&Data=" + kodyJsonObject).post(new FormBody.Builder().build()).build();

                                            timedataApi.sendLogData("REQUEST 4 - query", "http://tpcheck.ticketportal.cz/AddPrechody.ashx?SessionID=" + sessionID + "&Data=" + kodyJsonObject);
                                            //Log.i("REQUEST 4 - query", kodyJsonObject.toString());

                                            client.newCall(request4).enqueue(new Callback() {
                                                @Override
                                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                                    // @dev: added advanced logging
                                                    String errorMessage = e.getMessage();
                                                    String stackTrace = Log.getStackTraceString(e);

                                                    Log.e("OkHttpFailure", "Request failed: " + errorMessage);
                                                    Log.e("OkHttpFailure", "Stacktrace:\n" + stackTrace);

                                                    timedataApi.sendLogData("api error: validateQRCode", "onFailure validacia: " + errorMessage + "\n" + stackTrace);
                                                }
                                                @Override
                                                public void onResponse(@NonNull Call call, @NonNull Response response4) throws IOException {
                                                    timedataApi.sendLogData("api: validateQRCode", "onResponse OK");
                                                }
                                            });

                                        }else{
                                            timedataApi.sendLogData("api error: validateQRCode", "onResponse ZOZNAM PREDSTAVENI empty");
                                        }


                                    }else{
                                        Log.i("REQUEST 4 - status wrong", jsonStatusString);

                                        timedataApi.sendLogData("REQUEST 4 - status wrong", jsonStatusString);
                                    }

                                } catch (JSONException e) {
                                    Log.i("REQUEST 4 - ZOZNAM PREDSTAVENI ERROR", e.toString());

                                    timedataApi.sendLogData("REQUEST 4 - ZOZNAM PREDSTAVENI ERROR", e.toString());
                                }


                            }
                        });

                    }else{
                        timedataApi.sendLogData("api error: validateQRCode", "sessionID empty");
                    }

                } catch (JSONException e) {
                    timedataApi.sendLogData("api error: validateQRCode", "JSONException");
                }
            }
        });


    }


}
