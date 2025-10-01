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
import android.os.Looper;
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
import com.common.apiutil.util.SDKUtil;
import com.common.apiutil.util.StringUtil;
import com.common.callback.IDecodeReaderListener;
import com.timedata.ticketportal.classes.timedataApi;
import com.timedata.ticketportal.classes.timedataCoreFunctions;
import com.timedata.ticketportal.providers.ticketPortalProvider;
import com.timedata.ticketportal.vendor.KeyEventResolver;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements KeyEventResolver.OnScanSuccessListener {

    /*
        TO ENABLE KIOSK MODE:
        cmd: cd C:\Users\USERNAME\AppData\Local\Android\Sdk\platform-tools
        run: adb shell dpm set-device-owner com.timedata.ticketportal/.AppAdminReceiver

        TO REMOVE FROM PRODUCTION:
        run: adb shell dpm remove-active-admin com.timedata.ticketportal/.AppAdminReceiver
        run: adb uninstall com.timedata.ticketportal
     */

    // ==== Kiosk mode ====
    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;

    // ==== UI ====
    private TextView showTime;
    private TextView deviceIdEditText;

    // ==== Context & state ====
    private final Context c = this;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler resultHandler = new Handler(Looper.getMainLooper());

    private volatile int logoutTimeCLickCount = 0;
    private static final int logoutCode = 123654;

    // ==== QR ====
    private DecodeReader mDecodeReader;
    private KeyEventResolver mKeyEventResolver;
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    // Debounce: aby sme neposielali rovnaký kód opakovane, kým sa rieši výsledok
    private volatile String lastSubmittedCode = null;
    private final AtomicBoolean isCheckInFlight = new AtomicBoolean(false);

    public static String temporaryQrCode;

    private ticketPortalProvider provider; // 1 inštancia

    // ==== NFC ====
    private Nfc nfc; // inicializácia neskôr; predčasné new Nfc(this) v field-e môže byť náročné
    private ReadThread readThread;
    private volatile boolean isNfcOpen = false;
    private final AtomicBoolean nfcReading = new AtomicBoolean(false);

    private volatile String lastNfcUid = null;
    private long lastNfcTimestamp = 0;
    private static final long NFC_DEBOUNCE_MS = 2000; // 2 sekundy medzi rovnakými UID

    private volatile String lastQrCode = null;
    private long lastQrTimestamp = 0;
    private static final long QR_DEBOUNCE_MS = 2000; // 2 sekundy ochrana pred duplicitou


    // ==== Hodiny ====
    private final Runnable clockTicker = new Runnable() {
        private final SimpleDateFormat HH = new SimpleDateFormat("HH");
        private final SimpleDateFormat mm = new SimpleDateFormat("mm");
        private final SimpleDateFormat ss = new SimpleDateFormat("ss");
        {
            TimeZone tz = TimeZone.getTimeZone("Europe/Bratislava");
            HH.setTimeZone(tz);
            mm.setTimeZone(tz);
            ss.setTimeZone(tz);
        }
        @Override public void run() {
            final Date now = new Date();
            if (showTime != null) {
                showTime.setText(HH.format(now) + ":" + mm.format(now) + ":" + ss.format(now));
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    // ==== Výsledková slučka (číta provider.httpStatusCodeForRedirect) ====
    private final Runnable resultChecker = new Runnable() {
        @Override public void run() {
            loopResultCode();
            resultHandler.postDelayed(this, 1000); // raz za sekundu stačí
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize logging api ASAP, ale bez extra práce na UI vlákne
        timedataApi.initialize(this);

        // Immersive mode
        final int flags = timedataCoreFunctions.flags;
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(flags);
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decorView.setSystemUiVisibility(flags);
            }
        });

        setContentView(R.layout.scanning_page);

        // Device Policy
        mAdminComponentName = new ComponentName(this, AppAdminReceiver.class);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (mDevicePolicyManager.isDeviceOwnerApp(getPackageName())) {
            setDefaultCosuPolicies(true);
        }

        // SDK init (ľahké, necháme na main thread)
        SDKUtil.getInstance(this).initSDK();

        // UI elementy
        showTime = findViewById(R.id.showTime);
        deviceIdEditText = findViewById(R.id.device_id);

        if (showTime != null) {
            showTime.setOnClickListener(v -> {
                int local = logoutTimeCLickCount + 1;
                logoutTimeCLickCount = local;
                if (local > 4) logoutPopup();
            });
        }

        // Spusti hodiny (1×/s)
        mainHandler.post(clockTicker);

        // Device ID
        loadDeviceId();

        // Provider – jediná inštancia
        provider = new ticketPortalProvider();

        // NFC lazy init (až tu, nie vo fielde)
        nfc = new Nfc(this);
        tryOpenNfc();

        // NFC reader thread – spusti len 1×
        startNfcReaderIfNeeded();
    }

    // ======= NFC Thread =======
    private static boolean isChecking = false; // zachované pre kompatibilitu s tvojou logikou

    private class ReadThread extends Thread {
        @Override
        public void run() {
            isChecking = true;
            nfcReading.set(true);
            while (nfcReading.get()) {
                try {
                    // Krátky timeout a hneď spracovať
                    byte[] nfcData = nfc.activate(2 * 1000);
                    if (nfcData != null && nfcData.length > 0) {
                        Log.d("SHOW_NFC_DATA", new String(nfcData, StandardCharsets.UTF_8));
                        readNFCtag(nfcData);
                        // Po úspechu neruš loop, nech vieš čítať opakovane ďalšie tagy
                        // ale urob krátky “cooldown”, aby si nezaplavoval API duplicitami
                        Thread.sleep(150);
                    } else {
                        // ticho – bežná vec, žiadny tag; malá pauza
                        Thread.sleep(50);
                    }
                } catch (TimeoutException e) {
                    // bežné – pauza a ďalej
                } catch (CommonException e) {
                    Log.e("NFC_TAG", "CommonException: " + e.getMessage(), e);
                    // ak nastane chyba zariadenia, skús malú pauzu, potom pokračuj
                    try { Thread.sleep(200); } catch (InterruptedException ignore) {}
                } catch (InterruptedException e) {
                    // koniec vlákna
                    break;
                } catch (Exception e) {
                    Log.e("NFC_TAG", "Unexpected error: " + e.getMessage(), e);
                    // nech nepadne – malá pauza
                    try { Thread.sleep(200); } catch (InterruptedException ignore) {}
                }
            }
            isChecking = false;
        }

        public void stopReading() {
            nfcReading.set(false);
            interrupt();
        }
    }

    private void startNfcReaderIfNeeded() {
        if (readThread == null || !readThread.isAlive()) {
            readThread = new ReadThread();
            readThread.start();
        }
    }

    private void stopNfcReader() {
        if (readThread != null) {
            readThread.stopReading();
            readThread = null;
        }
    }

    private void tryOpenNfc() {
        try {
            if (!isNfcOpen) {
                nfc.open();
                isNfcOpen = true;
            }
        } catch (DeviceAlreadyOpenException e) {
            Log.w("NFC", "Device already open, skipping nfc.open()", e);
            isNfcOpen = true;
        } catch (CommonException e) {
            Log.e("NFC", "open() failed", e);
        }
    }

    private void tryCloseNfc() {
        try {
            if (isNfcOpen) {
                nfc.close();
                isNfcOpen = false;
            }
        } catch (Exception e) {
            Log.e("NFC", "Error closing NFC", e);
        }
    }

    // ======= NFC data =======
    protected void readNFCtag(byte[] nfcData) {
        if (nfcData == null || nfcData.length == 0) return;

        // UID extraction
        byte[] uid = new byte[nfcData[5]];
        System.arraycopy(nfcData, 6, uid, 0, nfcData[5]);
        String hexUid = StringUtil.toHexString(uid);

        long now = System.currentTimeMillis();

        // --- NFC debounce: ignoruj rovnakú kartu opakovane do 2 sekúnd ---
        if (hexUid.equals(lastNfcUid) && (now - lastNfcTimestamp < NFC_DEBOUNCE_MS)) {
            Log.d("NFC_TAG", "Duplicate NFC UID ignored: " + hexUid);
            return;
        }

        lastNfcUid = hexUid;
        lastNfcTimestamp = now;

        // Spusti result loop (ak ešte nebeží)
        startResultLoopOnce();

        Log.d("Raw NFC Data", Arrays.toString(nfcData));
        Log.d("NFC DATA", "nfcdata[" + hexUid + "]");

        // Pošli UID do API, ak nie je prázdny
        submitCodeIfNeeded(hexUid);
    }


    // ======= QR =======
    private void initQrIfNeeded() {
        if (mDecodeReader == null) mDecodeReader = new DecodeReader(this);
        if (mKeyEventResolver == null) mKeyEventResolver = new KeyEventResolver(this);

        mDecodeReader.setDecodeReaderListener(new IDecodeReaderListener() {
            @Override
            public void onRecvData(final byte[] data) {

                final String str = new String(trim(data), UTF8);
                long now = System.currentTimeMillis();

                // --- QR debounce: ak rovnaký kód v krátkom čase, ignoruj ---
                if (str.equals(lastQrCode) && (now - lastQrTimestamp < QR_DEBOUNCE_MS)) {
                    Log.d("QR_SCAN", "Duplicate QR ignored: " + str);
                    return;
                }

                lastQrCode = str;
                lastQrTimestamp = now;

                temporaryQrCode = str;
                timedataApi.sendLogData("TICKETPORTAL API - set temporaryQrCode", str);

                // Spusti slučku výsledku len raz
                startResultLoopOnce();

                // Debounce + submit
                submitCodeIfNeeded(str);


            }
        });
    }

    private void submitCodeIfNeeded(String code) {
        if (code == null || code.length() <= 3) return;

        // ak už prebieha check, ale kód je iný ako predchádzajúci, povolíme nový
        // ak je rovnaký a prebieha check, ignoruj
        String prev = lastSubmittedCode;
        if (isCheckInFlight.get() && code.equals(prev)) {
            return;
        }

        lastSubmittedCode = code;
        if (isCheckInFlight.compareAndSet(false, true)) {
            // spusti volanie
            new Thread(() -> {
                try {
                    Log.d("STRING FROM SCANNER checkQRCodeStatus", code);
                    provider.checkQRCodeStatus(code, c);
                } catch (Exception e) {
                    Log.e("API", "Error: " + e.getMessage(), e);
                } finally {
                    isCheckInFlight.set(false);
                }
            }).start();
        }
    }

    // ======= Result loop =======
    private volatile boolean resultLoopRunning = false;

    private void startResultLoopOnce() {
        if (!resultLoopRunning) {
            resultLoopRunning = true;
            resultHandler.postDelayed(resultChecker, 500);
        }
    }

    private void stopResultLoop() {
        resultLoopRunning = false;
        resultHandler.removeCallbacks(resultChecker);
    }

    public void loopResultCode() {
        // čítaj stav z jedinej inštancie providera
        String httpStatusCodeForRedirect = provider.httpStatusCodeForRedirect;

        if ("green".equals(httpStatusCodeForRedirect)) {
            timedataApi.sendLogData("loopResultCode - temporaryQrCode", temporaryQrCode);
            provider.httpStatusCodeForRedirect = "empty";
            stopResultLoop(); // už máme výsledok
            Intent intent_activity = new Intent(MainActivity.this, scanQrGreen.class);
            intent_activity.putExtra("temporaryQrCode", temporaryQrCode);
            startActivity(intent_activity);
        } else if ("red".equals(httpStatusCodeForRedirect)) {
            timedataApi.sendLogData("loopResultCode - temporaryQrCode", temporaryQrCode);
            provider.httpStatusCodeForRedirect = "empty";
            stopResultLoop(); // už máme výsledok
            startActivity(new Intent(MainActivity.this, scanQrRed.class));
        }
    }

    // ======= Device ID =======
    private void loadDeviceId() {
        SharedPreferences sp = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String deviceId = sp.getString("device_id", "- Unset -");
        if (deviceIdEditText != null) {
            deviceIdEditText.setText("Device ID: " + deviceId);
        }
    }

    // ======= Logout popup =======
    public void logoutPopup() {
        LayoutInflater inflater = LayoutInflater.from(c);
        View mView = inflater.inflate(R.layout.android_user_input_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(c).setView(mView).setCancelable(false);

        final EditText userInputDialogEditText = mView.findViewById(R.id.userInputDialog);

        builder.setPositiveButton("Send", (dialog, id) -> {
            String txt = userInputDialogEditText.getText() != null ? userInputDialogEditText.getText().toString() : "";
            if (txt.isEmpty()) {
                showError("Chyba", "Je potrebné zadať kod");
                return;
            }
            int inputValue;
            try {
                inputValue = Integer.parseInt(txt);
            } catch (NumberFormatException nfe) {
                showError("Chyba", "Nesprávny formát kódu");
                return;
            }
            if (inputValue != logoutCode) {
                showError("Chyba", "Nesprávne heslo pre odhlásenie");
            } else {
                startActivity(new Intent(MainActivity.this, settingsPage.class));
            }
            // skry klávesnicu
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
        });

        builder.setNegativeButton("Cancel", (d, id) -> d.cancel());
        builder.create().show();
    }

    private void showError(String title, String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    // ======= Lifecycle =======
    @Override
    public void onStart() {
        super.onStart();

        logoutTimeCLickCount = 0;

        if (mDevicePolicyManager != null && mDevicePolicyManager.isLockTaskPermitted(getPackageName())) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                setDefaultCosuPolicies(true);
                startLockTask();
            }
        }

        // turn off lights
        timedataCoreFunctions.greenLightOff(c);
        timedataCoreFunctions.redLightOff(c);
    }

    @Override
    protected void onResume() {
        super.onResume();

        logoutTimeCLickCount = 0;

        // QR init + open
        initQrIfNeeded();
        if (mDecodeReader != null) {
            // ak už bolo otvorené, netreba re-open – ale API očakáva open v onResume, necháme
            mDecodeReader.open(115200);
        }

        timedataCoreFunctions.greenLightOff(c);
        timedataCoreFunctions.redLightOff(c);

        // NFC open + reader
        tryOpenNfc();
        startNfcReaderIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopResultLoop();

        // NFC stop
        stopNfcReader();
        tryCloseNfc();

        // QR close
        if (mDecodeReader != null) {
            mDecodeReader.close();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopResultLoop();

        // NFC stop (pre istotu)
        stopNfcReader();
        tryCloseNfc();

        // QR close (pre istotu)
        if (mDecodeReader != null) {
            mDecodeReader.close();
        }

        // reset stavy
        isCheckInFlight.set(false);
        lastSubmittedCode = null;
        logoutTimeCLickCount = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // odstráň tickery
        mainHandler.removeCallbacksAndMessages(null);
        resultHandler.removeCallbacksAndMessages(null);
    }

    // ======= Key scan =======
    @Override
    public void onScanSuccess(String barcode) {
        Log.i("barcode", barcode);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if ("Virtual".equals(event.getDevice().getName())) {
            return super.dispatchKeyEvent(event);
        }
        if (mKeyEventResolver != null) {
            mKeyEventResolver.analysisKeyEvent(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ======= Pinning / COSU =======
    private void setDefaultCosuPolicies(boolean active){
        if (mDevicePolicyManager == null) return;

        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, active);
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, active);
        setUserRestriction(UserManager.DISALLOW_ADD_USER, active);
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, active);
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, active);

        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, active);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, active);

        enableStayOnWhilePluggedIn(active);

        if (active){
            mDevicePolicyManager.setSystemUpdatePolicy(
                    mAdminComponentName, SystemUpdatePolicy.createWindowedInstallPolicy(60, 120));
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName,null);
        }

        mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName, active ? new String[]{getPackageName()} : new String[]{});

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        if (active) {
            mDevicePolicyManager.addPersistentPreferredActivity(
                    mAdminComponentName, intentFilter,
                    new ComponentName(getPackageName(), MainActivity.class.getName()));
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                    mAdminComponentName, getPackageName());
        }
    }

    private void setUserRestriction(String restriction, boolean disallow){
        if (mDevicePolicyManager == null) return;
        if (disallow) {
            mDevicePolicyManager.addUserRestriction(mAdminComponentName,restriction);
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminComponentName,restriction);
        }
    }

    private void enableStayOnWhilePluggedIn(boolean enabled){
        if (mDevicePolicyManager == null) return;
        if (enabled) {
            mDevicePolicyManager.setGlobalSetting(
                    mAdminComponentName,
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    Integer.toString(BatteryManager.BATTERY_PLUGGED_AC
                            | BatteryManager.BATTERY_PLUGGED_USB
                            | BatteryManager.BATTERY_PLUGGED_WIRELESS));
        } else {
            mDevicePolicyManager.setGlobalSetting(
                    mAdminComponentName,
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    "0");
        }
    }
}
