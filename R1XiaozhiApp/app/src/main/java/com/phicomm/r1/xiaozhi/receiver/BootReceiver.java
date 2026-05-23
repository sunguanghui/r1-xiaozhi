package com.phicomm.r1.xiaozhi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;

/**
 * Receiver to auto-start services when R1 boots
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            
            Log.d(TAG, "Boot completed, starting Xiaozhi services");
            
            XiaozhiConfig config = new XiaozhiConfig(context);
            
            // Only auto-start if user has enabled the option
            if (config.isAutoStart()) {
                // Re-enable ADB TCP on boot so Wi-Fi ADB survives reboots
                try {
                    Runtime.getRuntime().exec(new String[]{"setprop", "service.adb.tcp.port", "5555"});
                    Runtime.getRuntime().exec(new String[]{"stop", "adbd"});
                    Runtime.getRuntime().exec(new String[]{"start", "adbd"});
                    Log.d(TAG, "ADB TCP port 5555 re-enabled on boot");
                } catch (Exception e) {
                    Log.w(TAG, "Could not re-enable ADB TCP (no root): " + e.getMessage());
                }

                // Start Xiaozhi connection service
                Intent xiaozhiIntent = new Intent(context, XiaozhiConnectionService.class);
                context.startService(xiaozhiIntent);

                // Start voice recognition service
                Intent voiceIntent = new Intent(context, VoiceRecognitionService.class);
                context.startService(voiceIntent);

                Log.d(TAG, "All services started successfully");
            } else {
                Log.d(TAG, "Auto-start disabled in config");
            }
        }
    }
}