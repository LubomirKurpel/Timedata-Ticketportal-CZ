package com.timedata.ticketportal.classes;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.common.CommonConstants;
import com.common.apiutil.pos.CommonUtil;

import java.util.Arrays;

public class timedataCoreFunctions extends Activity {

    public static final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    public static boolean contains( String haystack, String needle ) {
        haystack = haystack == null ? "" : haystack;
        needle = needle == null ? "" : needle;
        return haystack.toLowerCase().contains( needle.toLowerCase() );
    }

    public static byte[] trim(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0){ --i; }
        return Arrays.copyOf(bytes, i + 1);
    }

    public static Handler UIHandler;

    static
    {
        UIHandler = new Handler(Looper.getMainLooper());
    }
    public static void runOnUI(Runnable runnable) {
        UIHandler.post(runnable);
    }

    // otvorenie turniketu
    public static void otvorTurniket(Context context) {

        // otvor
        Log.w("turniket ", "otvaram");
        new CommonUtil(context).setRelayPower(1,1);
        timedataCoreFunctions.greenLightOn(context);

        timedataCoreFunctions.runOnUI(new Runnable() {
            public void run() {

        new android.os.Handler().postDelayed(
                () -> {
                    // zatvor
                    Log.i("turniket","zatvaram");
                    new CommonUtil(context).setRelayPower(1,0);
                    timedataCoreFunctions.redLightOn(context);
                }, 1000);

            }
        });
    }

    // ovladanie diodiek
    public static void greenLightOn(Context context){
      /*  redLightOff(context);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_1)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_2)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_3)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_4)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 100);*/
    }
    public static void greenLightOff(Context context){
      /*  new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_1)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_2)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_3)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_4)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.GREEN_LED)), 0);*/
    }
    public static void redLightOn(Context context){
       /* greenLightOff(context);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_1)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_2)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_3)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 100);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_4)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 100);*/
    }
    public static void redLightOff(Context context){
      /*  new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_1)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_2)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_3)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 0);
        new CommonUtil(context).setColorLed(Integer.parseInt(String.valueOf(CommonConstants.LedType.COLOR_LED_4)), Integer.parseInt(String.valueOf(CommonConstants.LedColor.RED_LED)), 0);*/
    }

}
