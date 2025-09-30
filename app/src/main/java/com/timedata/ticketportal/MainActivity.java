package com.timedata.ticketportal;

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

import com.common.apiutil.decode.DecodeReader;
import com.common.apiutil.pos.RS485Reader;
import com.common.apiutil.util.SDKUtil;
import com.common.callback.IDecodeReaderListener;
import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.classes.timedataCoreFunctions;
import com.timedata.ticketportal.providers.ticketPortalProvider;
import com.timedata.ticketportal.vendor.KeyEventResolver;

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
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
    private TextView textRestult, showTime;
    // nfc
    final Context c = this;
    private NfcAdapter nfcAdapter = null;
    private IntentFilter[] intentFiltersArray = null;
    private String[][] techListsArray = null;
    private PendingIntent pendingIntent = null;
    int logoutTimeCLickCount = 0;
    int logoutCode = 123654;

    private DecodeReader mDecodeReader;
    private KeyEventResolver mKeyEventResolver;
    boolean isreading;
    boolean read = false;
    private RS485Reader mRS485Reader;
    public static String temporaryQrCode;
    public static int canRedirect = 0;

    ticketPortalProvider ticketPortalProvider;

    private TextView deviceIdEditText;

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

        canRedirect = 0;

        // timedataApi.sendLogData("Log base64 test", "test");
        // Log.d("Log base64 test", "test");

        // Load saved device ID if it exists
        loadDeviceId();

        // timedataApi.sendLogData("Log device ID", "Log device ID as IMEI");

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


    @Override
    public void onStart() {
        // Consider locking your app here or by some other mechanism
        // Active Manager is supported on Android M
        super.onStart();

        canRedirect = 0;
        logoutTimeCLickCount = 0;

        if (mDevicePolicyManager.isLockTaskPermitted(this.getPackageName())) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                setDefaultCosuPolicies(true);
                startLockTask();
            }
        }

        handler.post(runnable);

        // turn off lights
        timedataCoreFunctions.greenLightOff(c);
        timedataCoreFunctions.redLightOff(c);
    }


    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        if (mDecodeReader != null) {
            mDecodeReader.close();
        }
        isreading = false;
        read = false;
        boolean read = false;
        canRedirect = 0;
        logoutTimeCLickCount = 0;
        handler.removeCallbacks(runnable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reset logout click counter
        logoutTimeCLickCount = 0;
        canRedirect = 0;

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

                            Log.d("canRedirect",String.valueOf(canRedirect));
                            timedataApi.sendLogData("canRedirect", String.valueOf(canRedirect));

                            if ( canRedirect == 0){

                                // api call
                                ticketPortalProvider = new ticketPortalProvider();
                                try {
                                    if (str.length() > 3) {
                                        Log.d("STRING FROM SCANNER checkQRCodeStatus",str);
                                        canRedirect = 1;
                                        ticketPortalProvider.checkQRCodeStatus(str, c);
                                    }
                                } catch (JSONException e) {
                                    canRedirect = 0;
                                    timedataApi.sendLogData("api error", "1");
                                    ticketPortalProvider.httpStatusCodeForRedirect = "empty";
                                    startActivity(new Intent(MainActivity.this, scanQrRed.class));
                                  //  finish();
                                } catch (IOException e) {
                                    canRedirect = 0;
                                    timedataApi.sendLogData("api error", "2");
                                    ticketPortalProvider.httpStatusCodeForRedirect = "empty";
                                    startActivity(new Intent(MainActivity.this, scanQrRed.class));
                                    //finish();
                                }
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
    }

    @Override
    protected void onPause() {
        super.onPause();
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