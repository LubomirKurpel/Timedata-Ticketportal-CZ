package com.timedata.ticketportal;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;

import com.timedata.ticketportal.classes.timedataApi;

public class scanQrRed extends Activity  {

    int miliSecondsToShowThisPage = 3000;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize logging api
        timedataApi.initialize(this);

        setContentView(R.layout.scan_qr_red);

        SharedPreferences preferences = this.getSharedPreferences("com.timedata.app", MODE_PRIVATE);
        String request_code_type =  preferences.getString("request_code_type", "Variable with this name does not exist");

    }

    @Override
    public void onStart() {
        super.onStart();
        ////////////////////////////////////////////////////////////////////// show countdown line
        new CountDownTimer(miliSecondsToShowThisPage, 150) {
            public void onTick(long millisUntilFinished) {
                int second = Math.toIntExact(millisUntilFinished / 150);
            }
            public void onFinish() {
                // redirect to scaning_page
                Log.d("redirect", "redirect to scaning_page");
               // startActivity(new Intent(scanQrRed.this, MainActivity.class));
                finish();
            }
        }.start();
        ////////////////////////////////////////////////////////////////////// show countdown line END
    }
}
