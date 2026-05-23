package com.phicomm.r1.xiaozhi.activation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Device Activator - Handle device activation flow
 * Based on py-xiaozhi/src/utils/device_activator.py
 */
public class DeviceActivator {
    
    private static final String TAG = "DeviceActivator";
    private static final String ACTIVATION_URL = "https://api.tenclass.net/xiaozhi/ota/activate";
    private static final int MAX_RETRIES = 60; // 5 minutes max
    private static final int RETRY_INTERVAL_MS = 5000; // 5 seconds
    
    private final Context context;
    private final DeviceFingerprint fingerprint;
    private final OTAConfigManager otaManager;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isActivating = new AtomicBoolean(false);
    
    // Activation data from server
    private String serverChallenge;
    private String verificationCode;
    
    private ActivationListener listener;
    
    public interface ActivationListener {
        void onActivationStarted(String verificationCode);
        void onActivationProgress(int attempt, int maxAttempts);
        void onActivationSuccess(String accessToken);
        void onActivationFailed(String error);
    }
    
    public DeviceActivator(Context context) {
        this.context = context.getApplicationContext();
        this.fingerprint = DeviceFingerprint.getInstance(context);
        this.otaManager = new OTAConfigManager(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setListener(ActivationListener listener) {
        this.listener = listener;
    }

    public boolean isActivating() {
        return isActivating.get();
    }
    
    /**
     * Start activation process
     * STEP 1: Fetch OTA config to get challenge + code from server
     */
    public void startActivation() {
        if (isActivating.getAndSet(true)) {
            Log.w(TAG, "Activation already in progress");
            return;
        }
        
        if (fingerprint.isActivated()) {
            String token = fingerprint.getAccessToken();
            if (token != null && !token.isEmpty()) {
                Log.i(TAG, "Device already activated, using saved token");
                notifySuccess(token);
                isActivating.set(false);
                return;
            }
            // Activated flag set but no token — re-fetch OTA to get real token
            Log.w(TAG, "Activated flag set but no token, re-fetching OTA...");
        }
        
        Log.i(TAG, "Starting activation - Fetching OTA config...");
        
        // STEP 1: Get OTA config (includes activation data if not activated)
        otaManager.fetchOTAConfig(new OTAConfigManager.OTACallback() {
            @Override
            public void onSuccess(OTAConfigManager.OTAResponse response) {
                handleOTAResponse(response);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "OTA config fetch failed: " + error);
                notifyError("Failed to fetch configuration: " + error);
                isActivating.set(false);
            }
        });
    }
    
    /**
     * Handle OTA response
     * STEP 2: Parse activation data or proceed if already activated
     */
    private void handleOTAResponse(OTAConfigManager.OTAResponse response) {
        // If OTA returned a real websocket token directly, save it and connect.
        // Reject placeholder values like "test-token" that the server returns
        // when the device is not yet bound to any user account.
        if (response.websocket != null && response.websocket.token != null
                && !response.websocket.token.isEmpty()
                && !response.websocket.token.equals("test-token")) {
            Log.i(TAG, "OTA returned access token directly");
            fingerprint.setActivationStatus(true);
            fingerprint.clearVerificationCode();
            fingerprint.setAccessToken(response.websocket.token);
            if (response.websocket.url != null && !response.websocket.url.isEmpty()) {
                fingerprint.setWebSocketUrl(response.websocket.url);
                Log.i(TAG, "Saved WebSocket URL: " + response.websocket.url);
            }
            notifySuccess(response.websocket.token);
            isActivating.set(false);
            return;
        }

        if (response.activation != null) {
            // Device needs activation - got challenge + code from server
            serverChallenge = response.activation.challenge;
            verificationCode = response.activation.code;
            
            Log.i(TAG, "Activation required - Challenge received from server");
            Log.i(TAG, "Verification code: " + verificationCode);
            
            // Notify UI to display code
            notifyVerificationCode(verificationCode);

            // Persist code so HTTP /pairing can expose it while waiting
            fingerprint.setVerificationCode(verificationCode);
            
            // STEP 3: Start polling activation API
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    performActivationPolling();
                }
            });
            
        } else {
            // No activation data - device already activated on server
            // OTA returned websocket config without activation challenge
            Log.i(TAG, "Device already activated on server");
            fingerprint.setActivationStatus(true);

            // OTA may still return test-token if the device was just bound to an agent.
            // Poll until a real token arrives (up to 3 minutes).
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    pollOTAForToken();
                }
            });
        }
    }
    
    /**
     * Poll OTA until a real token is returned (used after activation confirmed on server).
     * Runs on a background thread.
     */
    private void pollOTAForToken() {
        final int OTA_MAX_RETRIES = 36; // 36 × 5s = 180s
        for (int attempt = 1; attempt <= OTA_MAX_RETRIES && isActivating.get(); attempt++) {
            Log.i(TAG, "OTA token poll attempt " + attempt + "/" + OTA_MAX_RETRIES);
            try {
                final Object[] result = new Object[1];
                final Object lock = new Object();
                otaManager.fetchOTAConfig(new OTAConfigManager.OTACallback() {
                    @Override public void onSuccess(OTAConfigManager.OTAResponse r) {
                        synchronized (lock) { result[0] = r; lock.notifyAll(); }
                    }
                    @Override public void onError(String e) {
                        synchronized (lock) { result[0] = e; lock.notifyAll(); }
                    }
                });
                synchronized (lock) {
                    if (result[0] == null) lock.wait(12000);
                }
                if (result[0] instanceof OTAConfigManager.OTAResponse) {
                    OTAConfigManager.OTAResponse ota = (OTAConfigManager.OTAResponse) result[0];
                    if (ota.websocket != null
                            && ota.websocket.token != null
                            && !ota.websocket.token.isEmpty()
                            && !ota.websocket.token.equals("test-token")) {
                        if (ota.websocket.url != null && !ota.websocket.url.isEmpty()) {
                            fingerprint.setWebSocketUrl(ota.websocket.url);
                        }
                        fingerprint.setAccessToken(ota.websocket.token);
                        Log.i(TAG, "Got real token from OTA");
                        notifySuccess(ota.websocket.token);
                        isActivating.set(false);
                        return;
                    }
                }
                Log.i(TAG, "OTA still returning test-token, retrying in 5s...");
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.w(TAG, "OTA poll error: " + e.getMessage());
                try { Thread.sleep(RETRY_INTERVAL_MS); } catch (InterruptedException ie) { break; }
            }
        }
        if (isActivating.get()) {
            Log.e(TAG, "OTA did not return a real token after 3 minutes");
            notifyError("请前往 xiaozhi.me，将设备绑定到智能体后重启 App。");
            isActivating.set(false);
        }
    }

    /**
     * Cancel activation
     */
    public void cancelActivation() {
        isActivating.set(false);
        Log.i(TAG, "Activation cancelled");
    }
    
    /**
     * Perform activation polling
     * STEP 3: Poll activation API with server challenge
     */
    private void performActivationPolling() {
        try {
            String serialNumber = fingerprint.getSerialNumber();
            String deviceId = fingerprint.getMacAddress();
            
            if (serialNumber == null || deviceId == null) {
                notifyError("Device identity not found");
                return;
            }
            
            if (serverChallenge == null) {
                notifyError("Server challenge not received");
                return;
            }
            
            Log.i(TAG, "Starting activation polling with server challenge");
            
            // Retry loop
            for (int attempt = 1; attempt <= MAX_RETRIES && isActivating.get(); attempt++) {
                
                notifyProgress(attempt, MAX_RETRIES);
                
                try {
                    ActivationResponse response = sendActivationRequest(
                        serialNumber, deviceId, serverChallenge
                    );
                    
                    if (response.success) {
                        // Activation successful - user entered code on website
                        Log.i(TAG, "Activation successful! Polling OTA for real token (user must bind device to agent on xiaozhi.me)...");
                        fingerprint.setActivationStatus(true);
                        fingerprint.clearVerificationCode();

                        // Poll OTA until a real token is returned (up to 2 minutes).
                        // The user needs time to assign the device to an Agent on xiaozhi.me.
                        String accessToken = null;
                        final int OTA_MAX_RETRIES = 36; // 36 × 5s = 180s
                        for (int otaAttempt = 1; otaAttempt <= OTA_MAX_RETRIES && isActivating.get(); otaAttempt++) {
                            Log.i(TAG, "OTA token poll attempt " + otaAttempt + "/" + OTA_MAX_RETRIES + " — waiting for agent binding on xiaozhi.me...");
                            try {
                                final Object[] otaResult = new Object[1];
                                final Object lock2 = new Object();
                                otaManager.fetchOTAConfig(new OTAConfigManager.OTACallback() {
                                    @Override public void onSuccess(OTAConfigManager.OTAResponse r) {
                                        synchronized (lock2) { otaResult[0] = r; lock2.notifyAll(); }
                                    }
                                    @Override public void onError(String e) {
                                        synchronized (lock2) { otaResult[0] = e; lock2.notifyAll(); }
                                    }
                                });
                                synchronized (lock2) {
                                    if (otaResult[0] == null) lock2.wait(12000);
                                }
                                if (otaResult[0] instanceof OTAConfigManager.OTAResponse) {
                                    OTAConfigManager.OTAResponse otaResponse = (OTAConfigManager.OTAResponse) otaResult[0];
                                    if (otaResponse.websocket != null
                                            && otaResponse.websocket.token != null
                                            && !otaResponse.websocket.token.isEmpty()
                                            && !otaResponse.websocket.token.equals("test-token")) {
                                        accessToken = otaResponse.websocket.token;
                                        if (otaResponse.websocket.url != null && !otaResponse.websocket.url.isEmpty()) {
                                            fingerprint.setWebSocketUrl(otaResponse.websocket.url);
                                            Log.i(TAG, "Saved WebSocket URL: " + otaResponse.websocket.url);
                                        }
                                        Log.i(TAG, "Got real token from OTA after activation");
                                        break;
                                    } else {
                                        Log.i(TAG, "OTA still returning test-token, retrying in 5s...");
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "OTA poll interrupted: " + e.getMessage());
                            }
                            try { Thread.sleep(RETRY_INTERVAL_MS); } catch (InterruptedException ie) { break; }
                        }

                        if (accessToken == null) {
                            Log.e(TAG, "OTA did not return a real token after 2 minutes. Please bind the device to an Agent on xiaozhi.me.");
                            notifyError("请前往 xiaozhi.me，将设备绑定到一个智能体后重启 App。");
                            isActivating.set(false);
                            return;
                        }

                        fingerprint.setAccessToken(accessToken);
                        Log.i(TAG, "Access token saved: " + accessToken.substring(0, Math.min(30, accessToken.length())) + "...");

                        notifySuccess(accessToken);
                        isActivating.set(false);
                        return;
                        
                    } else if (response.statusCode == 202) {
                        // Still waiting for user input
                        Log.i(TAG, "Waiting for user to enter verification code on website...");
                        Thread.sleep(RETRY_INTERVAL_MS);
                        
                    } else {
                        // Other errors - log but continue retrying
                        Log.w(TAG, "Activation attempt " + attempt + " failed: " + response.error);
                        Thread.sleep(RETRY_INTERVAL_MS);
                    }
                    
                } catch (InterruptedException e) {
                    Log.i(TAG, "Activation interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Activation request failed", e);
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            // Max retries reached
            if (isActivating.get()) {
                notifyError("Activation timeout - max retries reached");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Activation polling failed", e);
            notifyError("Activation failed: " + e.getMessage());
        } finally {
            isActivating.set(false);
        }
    }
    
    /**
     * Send activation request to server
     * STEP 4: POST activation with server challenge + HMAC
     */
    private ActivationResponse sendActivationRequest(
        String serialNumber, String deviceId, String challenge
    ) throws Exception {
        
        // Generate HMAC with server challenge (NOT self-generated!)
        String hmac = fingerprint.generateHmac(challenge);

        if (hmac == null) {
            throw new Exception("Failed to generate HMAC");
        }

        // Enhanced logging for activation request
        Log.d(TAG, "=== ACTIVATION REQUEST ===");
        Log.d(TAG, "Serial Number: " + serialNumber);
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "Challenge: " + challenge);
        Log.d(TAG, "HMAC (first 30 chars): " + (hmac.length() > 30 ? hmac.substring(0, 30) + "..." : hmac));

        // Build request payload
        JSONObject payload = new JSONObject();
        JSONObject innerPayload = new JSONObject();
        innerPayload.put("algorithm", "hmac-sha256");
        innerPayload.put("serial_number", serialNumber);
        innerPayload.put("challenge", challenge);
        innerPayload.put("hmac", hmac);
        payload.put("Payload", innerPayload);

        Log.d(TAG, "Request Payload: " + payload.toString());
        Log.d(TAG, "==========================");
        
        // Send HTTP request
        URL url = new URL(ACTIVATION_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Activation-Version", "2");
            conn.setRequestProperty("Device-Id", deviceId);
            conn.setRequestProperty("Client-Id", java.util.UUID.randomUUID().toString());
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
            }

            int statusCode = conn.getResponseCode();

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    statusCode >= 200 && statusCode < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            Log.d(TAG, "Response (" + statusCode + "): " + response);
            
            // Parse response
            ActivationResponse result = new ActivationResponse();
            result.statusCode = statusCode;
            
            if (statusCode == 200) {
                // Success
                result.success = true;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.accessToken = json.optString("access_token", null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse success response", e);
                }
                
            } else if (statusCode == 202) {
                // Waiting for verification
                result.success = false;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.verificationCode = json.optString("code", null);
                    result.message = json.optString("message", "Please enter verification code");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse 202 response", e);
                }
                
            } else {
                // Error
                result.success = false;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.error = json.optString("error", "Unknown error");
                } catch (Exception e) {
                    result.error = "HTTP " + statusCode + ": " + response;
                }
            }
            
            return result;
            
        } finally {
            conn.disconnect();
        }
    }
    
    // Notification helpers (main thread)
    
    private void notifyVerificationCode(final String code) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationStarted(code);
                }
            });
        }
    }
    
    private void notifyProgress(final int attempt, final int max) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationProgress(attempt, max);
                }
            });
        }
    }
    
    private void notifySuccess(final String token) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationSuccess(token);
                }
            });
        }
    }
    
    private void notifyError(final String error) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationFailed(error);
                }
            });
        }
    }
    
    /**
     * Check if device is activated
     */
    public boolean isActivated() {
        return fingerprint.isActivated() && fingerprint.getAccessToken() != null;
    }
    
    /**
     * Get access token
     */
    public String getAccessToken() {
        return fingerprint.getAccessToken();
    }
    
    /**
     * Reset activation (for testing)
     */
    public void resetActivation() {
        fingerprint.resetIdentity();
        Log.i(TAG, "Activation reset");
    }
    
    /**
     * Generate access token from device credentials
     * Fallback if server doesn't provide token
     */
    private String generateAccessToken(String deviceId, String serialNumber) {
        try {
            // Simple token generation: Base64(deviceId:serialNumber:timestamp)
            String tokenData = deviceId + ":" + serialNumber + ":" + System.currentTimeMillis();
            return android.util.Base64.encodeToString(
                tokenData.getBytes("UTF-8"),
                android.util.Base64.NO_WRAP
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate access token", e);
            return null;
        }
    }
    
    /**
     * Response from activation API
     */
    private static class ActivationResponse {
        boolean success;
        int statusCode;
        String verificationCode;
        String message;
        String accessToken;
        String error;
    }
}