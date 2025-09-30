package com.timedata.ticketportal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;

import com.common.CommonConstants;
import com.common.apiutil.pos.RS485Reader;
import com.common.callback.IRSReaderListener;
import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.providers.ticketPortalProvider;

import org.json.JSONException;

public class scanQrGreen extends Activity  {

    private RS485Reader mRS485Reader;

    int miliSecondsToShowThisPage = 5000;
    public String temporaryQrCode;

    int mozemPretocit = 0;

    final Context c = this;

    ticketPortalProvider ticketPortalProvider;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize logging api
        timedataApi.initialize(this);

        setContentView(R.layout.scan_qr_green);

        SharedPreferences preferences = this.getSharedPreferences("com.timedata.app", MODE_PRIVATE);
        String request_code_type =  preferences.getString("request_code_type", "Variable with this name does not exist");


        // LISTENER 485
        mRS485Reader = new RS485Reader(this);
        mRS485Reader.rsOpen(CommonConstants.RS485Type.RS485_1, 19200);
        mRS485Reader.setMode(CommonConstants.RSMode.RECV_MODE);
        mRS485Reader.setRSReaderListener(rs485listener);

    }

    @Override
    public void onStart() {
        super.onStart();

        mozemPretocit = 0;

        ////////////////////////////////////////////////////////////////////// show countdown line
        new CountDownTimer(miliSecondsToShowThisPage, 150) {
            public void onTick(long millisUntilFinished) {
                int second = Math.toIntExact(millisUntilFinished / 150);
            }
            public void onFinish() {
                // redirect to scaning_page

                if(mozemPretocit == 0){
                    mozemPretocit = 1;
                    Log.d("redirect", "redirect to scaning_page");
                   // startActivity(new Intent(scanQrGreen.this, MainActivity.class));
                    finish();
                }else{
                    Log.d("mozemPretocit", "1");
                }

            }
        }.start();
        ////////////////////////////////////////////////////////////////////// show countdown line END

        // get temporery qr
        temporaryQrCode = MainActivity.temporaryQrCode;
        timedataApi.sendLogData("TICKETPORTAL CZ - scanQrGreen - temporaryQrCode 2", temporaryQrCode);
/*
        // umele pretočenie
        // API
        ticketPortalProvider = new ticketPortalProvider();
        try {
            timedataApi.sendLogData("RS485 validate", "Data sent to validation class");
            ticketPortalProvider.validateQRCode(trim(temporaryQrCode), c);

            if(mozemPretocit == 0){
                mozemPretocit = 1;
                Log.d("redirect", "redirect to scaning_page");
                // startActivity(new Intent(scanQrGreen.this, MainActivity.class));
                finish();
            }else{
                Log.d("mozemPretocit", "1");
            }

        } catch (JSONException e) {

        }*/
    }

    private final IRSReaderListener rs485listener = new IRSReaderListener() {
        @Override
        public void onRecvData(final byte[] data) {
            final String tempString = ByteArrayToHexString(data);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // VALUE
                    String resultTelpo;
                    if(ByteArrayToHexString(data).length() > 8){

                        timedataApi.sendLogData("resultTelpo before cutting", ByteArrayToHexString(data));
                        Log.i("resultTelpo before cutting", ByteArrayToHexString(data));

                        resultTelpo = ByteArrayToHexString(data).substring(26, 28);
                    }else{
                        resultTelpo = ByteArrayToHexString(data);
                    }

                    if(ByteArrayToHexString(data).length() > 8) {

                        timedataApi.sendLogData("resultTelpo after cutting", resultTelpo);
                        Log.i("resultTelpo after cutting", resultTelpo);

                        if (resultTelpo.equals("61") || resultTelpo.equals("62")) {
                            // pretočene
                            Log.i("pretočenie", "pretočil");
                            timedataApi.sendLogData("RS485 pretočenie", "pretočil");

                            timedataApi.sendLogData("Temporary QR Code - zneaktivnenie", temporaryQrCode);
                            Log.d("Temporary QR Code - zneaktivnenie", temporaryQrCode);

                            // API
                            ticketPortalProvider = new ticketPortalProvider();
                            try {
                                timedataApi.sendLogData("RS485 validate", "Data sent to validation class");
                                ticketPortalProvider.validateQRCode(trim(temporaryQrCode), c);

                                // Redirect back to scanning page
                                if(mozemPretocit == 0){
                                    mozemPretocit = 1;
                                   // startActivity(new Intent(scanQrGreen.this, MainActivity.class));
                                    finish();
                                }else{
                                    Log.d("mozemPretocit", "1");
                                }

                            } catch (JSONException e) {

                            }

                        } else {
                            // nepretočene
                            Log.i("pretočenie", "NEpretočil");
                            timedataApi.sendLogData("RS485 pretočenie", "NEpretočil");
                        }

                    }
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRS485Reader.rsDestroy();
    }

    private String ByteArrayToHexString(byte[] inarray) {
        int i, j, in;
        String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A",
                "B", "C", "D", "E", "F"};
        String out = "";

        for (j = 0; j < inarray.length; ++j) {
            in = inarray[j] & 0xff;
            i = (in >> 4) & 0x0f;
            out += hex[i];
            i = in & 0x0f;
            out += hex[i];
        }
        return out;
    }

    private String trim(String text) {
        String str;
        str = text.replaceAll("\\r\\n|\\r|\\n", "");
        return str;
    }

}
