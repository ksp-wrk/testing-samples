package com.example.simnumberapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.UssdResponseCallback;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private final String TAG = "SystemUssd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button btn = new Button(this);
        btn.setText("Run USSD on both SIMs");
        setContentView(btn);

        btn.setOnClickListener(v -> new Thread(() -> runUssdAndSave(MainActivity.this)).start());
    }

    private void runUssdAndSave(Context ctx) {
        try {
            SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(ctx, "No active SIMs found", Toast.LENGTH_SHORT).show());
                return;
            }

            for (SubscriptionInfo sub : subs) {
                int subId = sub.getSubscriptionId();
                Log.i(TAG, "Sending USSD for subId=" + subId + ", slot=" + sub.getSimSlotIndex());

                TelephonyManager tm = ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE))
                        .createForSubscriptionId(subId);

                Executor executor = Runnable::run;

                UssdResponseCallback callback = new UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                        Log.i(TAG, "USSD response for subId=" + subId + ": " + response);
                        List<String> numbers = extractNumbers(response.toString());
                        if (!numbers.isEmpty()) saveNumbersToFile(numbers);
                    }

                    @Override
                    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                        Log.w(TAG, "USSD failed for subId=" + subId + ", code=" + failureCode);
                    }
                };

                String ussd = "*2#";
                try {
                    tm.sendUssdRequest(ussd, callback, executor);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send USSD on subId=" + subId + ": " + e.getMessage());
                }
            }

            runOnUiThread(() -> Toast.makeText(ctx, "USSD requests sent (check log/file)", Toast.LENGTH_SHORT).show());

        } catch (Exception e) {
            Log.e(TAG, "Error running USSD: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private List<String> extractNumbers(String text) {
        List<String> result = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\+?\d{8,15}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private void saveNumbersToFile(List<String> numbers) {
        try {
            File file = new File("/sdcard/phone_numbers.txt");
            if (!file.exists()) file.createNewFile();
            FileWriter writer = new FileWriter(file, true);
            for (String n : numbers) {
                writer.write(n + "\n");
            }
            writer.close();
            Log.i(TAG, "Saved numbers: " + numbers);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write file: " + e.getMessage());
        }
    }
}
