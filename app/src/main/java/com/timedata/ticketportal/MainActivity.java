package com.timedata.ticketportal;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static com.timedata.ticketportal.classes.timedataCoreFunctions.trim;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.common.apiutil.CommonException;
import com.common.apiutil.DeviceAlreadyOpenException;
import com.common.apiutil.TimeoutException;
import com.common.apiutil.decode.DecodeReader;
import com.common.apiutil.nfc.Nfc;
import com.common.apiutil.pos.RS485Reader;
import com.common.apiutil.util.SDKUtil;
import com.common.apiutil.util.StringUtil;
import com.common.callback.IDecodeReaderListener;
import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.classes.timedataCoreFunctions;
import com.timedata.ticketportal.providers.ticketPortalProvider;
import com.timedata.ticketportal.vendor.KeyEventResolver;

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements KeyEventResolver.OnScanSuccessListener {

    /*

        TO ENABLE KIOSK MODE:
        cmd: cd C:\Users\USERNAME\AppData\Local\Android\Sdk\platform-tools
        run: adb shell dpm set-device-owner com.timedata.ticketportal/.AppAdminReceiver

        TO REMOVE FROM PRODUCTION:
        run: adb shell dpm remove-active-admin com.timedata.ticketportal/.AppAdminReceiver
        run: adb uninstall com.timedata.ticketportal

     */

    // Kiosk mode
    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;

    // QR code scanner
    private TextView showTime;
    // nfc
    final Context c = this;
    int logoutTimeCLickCount = 0;
    int logoutCode = 123654;

    private DecodeReader mDecodeReader;
    private KeyEventResolver mKeyEventResolver;
    boolean isreading;
    boolean read = false;
    public static String temporaryQrCode;

    ticketPortalProvider ticketPortalProvider;

    private TextView deviceIdEditText;

    // NFC scanning
    Nfc nfc = new Nfc(this);

    private ReadThread readThread;

    private boolean isNfcOpen = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize logging api
        timedataApi.initialize(this);

        // Immersive mode
        int flags = timedataCoreFunctions.flags;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
        {
            @Override
            public void onSystemUiVisibilityChange(int visibility)
            {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                {
                    decorView.setSystemUiVisibility(flags);
                }
            }
        });

        setContentView(R.layout.scanning_page);

        // Retrieve Device Policy Manager so that we can check whether we can
        // lock to screen later
        mAdminComponentName = new ComponentName(this,AppAdminReceiver.class);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(mDevicePolicyManager.isDeviceOwnerApp(getPackageName())){
            // App is whitelisted
            setDefaultCosuPolicies(true);
        }
        else {
            // did you provision the app using <adb shell dpm set-device-owner ...> ?
        }

        SDKUtil.getInstance(this).initSDK();

        // Show time in header
        showTime = (TextView) findViewById(R.id.showTime);

        showTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logoutTimeCLickCount++;
                if(logoutTimeCLickCount > 4){
                    logoutPopup();
                }
            }
        });

        Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable() {
            @Override
            public void run() {
                handler2.postDelayed(this, 500);
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

        // timedataApi.sendLogData("Log base64 test", "test");
        // Log.d("Log base64 test", "test");

        // Load saved device ID if it exists
        loadDeviceId();

        // timedataApi.sendLogData("Log device ID", "Log device ID as IMEI");


        // NFC - New android 12 - tps900
        try {
            nfc.open();
        } catch (CommonException e) {
            e.printStackTrace();
        }

        readThread = new ReadThread();
        readThread.start();

        /*
        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAG_MUTABLE);
        intentFiltersArray = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
        techListsArray = new String[][]{new String[]{NfcA.class.getName()}, new String[]{NfcB.class.getName()}, new String[]{IsoDep.class.getName()}};
         */

    }

    // NFC - New android 12 - tps900
    private static boolean isChecking = false;

    private class ReadThread extends Thread {
        byte[] nfcData = null;

        @Override
        public void run() {
            isChecking = true;
            while (isChecking) {
                try {
                    // Attempt to activate the NFC for 2 seconds
                    nfcData = nfc.activate(2 * 1000); // 2 seconds timeout

                    if (nfcData != null) {
                        // Successfully received NFC data
                        Log.d("SHOW_NFC_DATA", new String(nfcData, StandardCharsets.UTF_8));

                        // handler.sendMessage(handler.obtainMessage(SHOW_NFC_DATA, nfcData));

                        // Process the NFC data
                        readNFCtag(nfcData);

                        // Stop the loop after successful read
                        isChecking = false;
                    } else {
                        // Handle case where no data is received within timeout
                        Log.d("NFC_TAG", "No NFC data received. Retrying...");
                    }
                } catch (TimeoutException e) {
                    // Handle the specific timeout exception gracefully
                    Log.w("NFC_TAG", "NFC activation timed out. Retrying...");
                } catch (CommonException e) {
                    // Log and handle any other NFC-related exceptions
                    Log.e("NFC_TAG", "An error occurred during NFC activation: " + e.getMessage());
                    e.printStackTrace();

                    // Optionally, break out of the loop to prevent infinite retries
                    isChecking = false;
                } catch (Exception e) {
                    // Catch any unexpected exceptions to prevent app crash
                    Log.e("NFC_TAG", "Unexpected error: " + e.getMessage());
                    e.printStackTrace();

                    // Stop checking if an unexpected error occurs
                    isChecking = false;
                }
            }
        }

        // Method to stop the thread gracefully
        public void stopReading() {
            isChecking = false;
            this.interrupt(); // Interrupt the thread if it's waiting or sleeping
        }
    }


    protected void readNFCtag(byte[] nfcData) {
        if (nfcData != null && nfcData.length > 0) {
            System.out.println("New tag found !!!");

            // Start loopResultCode handler thread
            startResultLoop();


            Log.d("Raw NFC Data", Arrays.toString(nfcData));

            byte[] uid = new byte[nfcData[5]];

            System.arraycopy(nfcData, 6, uid, 0, nfcData[5]);

            Log.d("NFC DATA", "nfcdata["+ StringUtil.toHexString(uid) +"]");

            String str = StringUtil.toHexString(uid);



            // Optional: format as A1-B2-C3-D4
            /*
            String formattedUID = formatHexUID(StringUtil.toHexString(uid));

            // formattedUID = "40-71-B4-0C";

            Log.d("Formatted NFC UID", formattedUID);

            // Log the UID
            timedataApi.sendLogData("Tag UID", formattedUID);

            // Prepare the intent for the next activity
            Intent intentActivity = new Intent(this, scanningPageResult.class);
            intentActivity.putExtra("UID", formattedUID);

            int finalResult = 0;
            int finalResultStatus = 0;

            // Pass data to the next activity
            intentActivity.putExtra("locker_value", String.valueOf(finalResult));
            intentActivity.putExtra("locker_status", String.valueOf(finalResultStatus));
            startActivity(intentActivity);
            */

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    // api call
                    ticketPortalProvider = new ticketPortalProvider();
                    if (str.length() > 3) {
                        Log.d("STRING FROM SCANNER checkQRCodeStatus",str);

                        new Thread(() -> {
                            try {
                                ticketPortalProvider.checkQRCodeStatus(str, c);
                            } catch (Exception e) {
                                Log.e("API", "Error: " + e.getMessage());
                            }
                        }).start();

                    }

                }
            });
            
            
            

            

        } else {
            System.out.println("No new tag found !!!");
        }
    }

    private String formatHexUID(String hexUID) {
        int interval = 2;
        char separator = '-';

        StringBuilder sb = new StringBuilder(hexUID);
        for (int i = 0; i < hexUID.length() / interval; i++) {
            sb.insert(((i + 1) * interval) + i, separator);
        }

        if (sb.toString().endsWith("-")) {
            return sb.substring(0, sb.length() - 1);
        }
        return sb.toString();
    }



    // Method to load the device ID from SharedPreferences
    private void loadDeviceId() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String deviceId = sharedPreferences.getString("device_id", "- Unset -");

        deviceIdEditText = findViewById(R.id.device_id);

        deviceIdEditText.setText("Device ID: " + deviceId);
    }

    public void loopResultCode(){

        ticketPortalProvider = new ticketPortalProvider();
        String httpStatusCodeForRedirect = ticketPortalProvider.httpStatusCodeForRedirect;

        if (httpStatusCodeForRedirect.equals("green")) {

            timedataApi.sendLogData("loopResultCode - temporaryQrCode 1", temporaryQrCode);

            ticketPortalProvider.httpStatusCodeForRedirect = "empty";
            Intent intent_activity = new Intent(MainActivity.this, scanQrGreen.class);
            intent_activity.putExtra("temporaryQrCode", temporaryQrCode);
            startActivity(intent_activity);
           // finish();
        }
        if (httpStatusCodeForRedirect.equals("red")) {

            timedataApi.sendLogData("loopResultCode - temporaryQrCode 1", temporaryQrCode);
            ticketPortalProvider.httpStatusCodeForRedirect = "empty";
            startActivity(new Intent(MainActivity.this, scanQrRed.class));
           // finish();
        }
    }

    public void logoutPopup() {
        // dialog
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(c);
        View mView = layoutInflaterAndroid.inflate(R.layout.android_user_input_dialog, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(c);
        alertDialogBuilderUserInput.setView(mView);

        final EditText userInputDialogEditText = (EditText) mView.findViewById(R.id.userInputDialog);

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface alertDialogBuilderUserInput, int id) {
                        if (!userInputDialogEditText.getText().toString().matches("")) {
                            int inputValue = Integer.parseInt(userInputDialogEditText.getText().toString());
                            if (inputValue != logoutCode) {
                                // error dialog
                                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.setTitle("Chyba");
                                alertDialog.setMessage("Nesprávne heslo pre odhlásenie");
                                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                // hide keyboard
                                                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE); imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);

                                            }
                                        });
                                alertDialog.show();

                            } else {
                                // Redirect to Settings Page
                                startActivity(new Intent(MainActivity.this, settingsPage.class));
                               // finish();
                            }
                        }else{
                            // error dialog
                            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                            alertDialog.setTitle("Chyba");
                            alertDialog.setMessage("Je potrebné zadať kod");
                            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            // hide keyboard
                                            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE); imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);

                                        }
                                    });
                            alertDialog.show();
                        }
                    }
                })

                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogBox, int id) {
                                dialogBox.cancel();
                            }
                        });
        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }

    Handler handler = new Handler();

    /*
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            //Your function
            loopResultCode();

            if(1 == 0){
                //showBookedDialog()
            }else{
                //restarting the runnable
                handler.postDelayed(this, 500);
            }
        }
    };
    */


    private void startResultLoop() {
        handler.postDelayed(resultChecker, 500);
    }

    private void stopResultLoop() {
        handler.removeCallbacks(resultChecker);
    }

    Runnable resultChecker = new Runnable() {
        @Override
        public void run() {
            loopResultCode();
            handler.postDelayed(this, 1000); // raz za sekundu stačí
        }
    };


    @Override
    public void onStart() {
        // Consider locking your app here or by some other mechanism
        // Active Manager is supported on Android M
        super.onStart();

        logoutTimeCLickCount = 0;

        if (mDevicePolicyManager.isLockTaskPermitted(this.getPackageName())) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                setDefaultCosuPolicies(true);
                startLockTask();
            }
        }

        // handler.post(runnable);

        // turn off lights
        timedataCoreFunctions.greenLightOff(c);
        timedataCoreFunctions.redLightOff(c);
    }


    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();

        stopResultLoop();

        if (readThread != null) {
            readThread.stopReading();
            readThread = null;
        }

        try {
            if (isNfcOpen) {
                nfc.close();
                isNfcOpen = false;
            }
        } catch (Exception e) {
            Log.e("NFC", "Error closing NFC", e);
        }

        if (mDecodeReader != null) {
            mDecodeReader.close();
            mDecodeReader = null;
        }

        isreading = false;
        read = false;
        boolean read = false;
        logoutTimeCLickCount = 0;
        // handler.removeCallbacks(runnable);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reset logout click counter
        logoutTimeCLickCount = 0;

        ////////////////// skenovanie cez QR

        if (mDecodeReader == null) {
            mDecodeReader = new DecodeReader(this);//初始化
        }
        mKeyEventResolver = new KeyEventResolver(this);
        mDecodeReader.setDecodeReaderListener(new IDecodeReaderListener() {

            @Override
            public void onRecvData(final byte[] data) {

                try {
                    // SKENOVANIE
                    final String str = new String(trim(data), "UTF-8");
                    temporaryQrCode = str;

                    timedataApi.sendLogData("TICKETPORTAL API - set temporaryQrCode", str);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            // api call
                            ticketPortalProvider = new ticketPortalProvider();
                            if (str.length() > 3) {
                                Log.d("STRING FROM SCANNER checkQRCodeStatus",str);

                                // Start loopResultCode handler thread
                                startResultLoop();

                                new Thread(() -> {
                                    try {
                                        ticketPortalProvider.checkQRCodeStatus(str, c);
                                    } catch (Exception e) {
                                        Log.e("API", "Error: " + e.getMessage());
                                    }
                                }).start();

                            }

                        }
                    });

                } catch (UnsupportedEncodingException e) {
                   // e.printStackTrace();
                }
            }
        });

        timedataCoreFunctions.greenLightOff(c);
        timedataCoreFunctions.redLightOff(c);

        int ret = mDecodeReader.open(115200);
        ////////////////// skenovanie cez QR


        // Reset logout click counter
        logoutTimeCLickCount = 0;

        // NFC
        try {
            if (!isNfcOpen) { // Check the custom flag
                nfc.open();
                isNfcOpen = true; // Update the flag
            }
        } catch (DeviceAlreadyOpenException e) {
            Log.w("NFC", "Device already open, skipping nfc.open()", e);
            isNfcOpen = true; // Set the flag to true as it's already open
        } catch (CommonException e) {
            e.printStackTrace();
        }

        readThread = new ReadThread();
        readThread.start();

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopResultLoop();

        if (readThread != null) {
            readThread.stopReading();
            readThread = null;
        }

        try {
            if (isNfcOpen) {
                nfc.close();
                isNfcOpen = false;
            }
        } catch (Exception e) {
            Log.e("NFC", "Error closing NFC", e);
        }

        if (mDecodeReader != null) {
            mDecodeReader.close();
            mDecodeReader = null;
        }
    }

    @Override
    public void onScanSuccess(String barcode) {
        Log.i("barcode",barcode);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if ("Virtual".equals(event.getDevice().getName())) {
            return super.dispatchKeyEvent(event);
        }
        mKeyEventResolver.analysisKeyEvent(event);
        return true;
    }

    // Pinning

    private void setDefaultCosuPolicies(boolean active){

        // Set user restrictions
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, active);
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, active);
        setUserRestriction(UserManager.DISALLOW_ADD_USER, active);
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, active);
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, active);

        // Disable keyguard and status bar
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, active);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, active);

        // Enable STAY_ON_WHILE_PLUGGED_IN
        enableStayOnWhilePluggedIn(active);

        // Set system update policy
        if (active){
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, SystemUpdatePolicy.createWindowedInstallPolicy(60, 120));
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName,null);
        }

        // set this Activity as a lock task package
        mDevicePolicyManager.setLockTaskPackages(mAdminComponentName,active ? new String[]{getPackageName()} : new String[]{});

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        if (active) {
            // set Cosu activity as home intent receiver so that it is started
            // on reboot
            mDevicePolicyManager.addPersistentPreferredActivity(mAdminComponentName, intentFilter, new ComponentName(getPackageName(), MainActivity.class.getName()));
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mAdminComponentName, getPackageName());
        }
    }

    private void setUserRestriction(String restriction, boolean disallow){
        if (disallow) {
            mDevicePolicyManager.addUserRestriction(mAdminComponentName,restriction);
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminComponentName,restriction);
        }
    }

    private void enableStayOnWhilePluggedIn(boolean enabled){
        if (enabled) {
            mDevicePolicyManager.setGlobalSetting(mAdminComponentName, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,Integer.toString(BatteryManager.BATTERY_PLUGGED_AC| BatteryManager.BATTERY_PLUGGED_USB| BatteryManager.BATTERY_PLUGGED_WIRELESS));
        } else {
            mDevicePolicyManager.setGlobalSetting(mAdminComponentName,Settings.Global.STAY_ON_WHILE_PLUGGED_IN,"0");
        }
    }





}