package com.phicomm.r1.xiaozhi.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.activation.DeviceActivator;
import com.phicomm.r1.xiaozhi.activation.DeviceFingerprint;
import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.core.DeviceState;
import com.phicomm.r1.xiaozhi.core.EventBus;
import com.phicomm.r1.xiaozhi.core.ListeningMode;
import com.phicomm.r1.xiaozhi.core.XiaozhiCore;
import com.phicomm.r1.xiaozhi.events.ConnectionEvent;
import com.phicomm.r1.xiaozhi.events.MessageReceivedEvent;
import com.phicomm.r1.xiaozhi.util.ErrorCodes;
import com.phicomm.r1.xiaozhi.util.TrustAllCertificates;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLParameters;

/**
 * 官方协议适配版 XiaozhiConnectionService
 */
public class XiaozhiConnectionService extends Service {

    private static final String TAG = "XiaozhiConnection";
    private static final int MAX_RETRIES = 3;
    private static final int NOTIFICATION_ID = 1001;

    private WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private ConnectionListener connectionListener;
    private XiaozhiCore core;
    private EventBus eventBus;
    private DeviceActivator deviceActivator;
    private DeviceFingerprint deviceFingerprint;
    private Handler retryHandler;
    private int retryCount = 0;
    private boolean isRetrying = false;
    
    public class LocalBinder extends Binder {
        public XiaozhiConnectionService getService() { return XiaozhiConnectionService.this; }
    }
    
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onActivationRequired(String verificationCode);
        void onActivationProgress(int attempt, int maxAttempts);
        void onPairingSuccess();
        void onPairingFailed(String error);
        void onMessage(String message);
        void onError(String error);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 保持前台服务
        Intent notificationIntent = new Intent(this, com.phicomm.r1.xiaozhi.ui.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
            .setContentTitle("Xiaozhi Service")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build();
        startForeground(NOTIFICATION_ID, notification);

        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        deviceFingerprint = DeviceFingerprint.getInstance(this);
        deviceActivator = new DeviceActivator(this);
        core.setConnectionService(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        retryHandler = new Handler();
        if (intent != null && "SEND_AUDIO".equals(intent.getAction())) {
            byte[] audioData = intent.getByteArrayExtra("audio_data");
            if (audioData != null) sendAudioToServer(audioData, 16000, 1);
            return START_STICKY;
        }
        
        // 自动激活逻辑
        if (deviceActivator.isActivated()) {
            connect();
        } else {
            deviceActivator.startActivation();
        }
        return START_STICKY;
    }

    public void connect() {
        if (webSocketClient != null && webSocketClient.isOpen()) return;
        String accessToken = deviceFingerprint.getValidAccessToken();
        if (accessToken != null) connectWithToken(accessToken);
    }
    
    private void connectWithToken(final String accessToken) {
        try {
            XiaozhiConfig config = new XiaozhiConfig(this);
            URI serverUri = new URI(config.getActiveUrl());
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            
            webSocketClient = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake h) {
                    Log.i(TAG, "连接成功，准备握手");
                    sendHelloMessage();
                }

                @Override
                public void onMessage(String message) { handleMessage(message); }
                
                @Override
                public void onMessage(java.nio.ByteBuffer bytes) {
                    // 官方协议音频回传处理
                    Log.d(TAG, "收到二进制音频数据: " + bytes.remaining());
                }

                @Override
                public void onClose(int c, String r, boolean rem) { if (rem) scheduleReconnect(ErrorCodes.WEBSOCKET_ERROR); }
                
                @Override
                public void onError(Exception ex) { scheduleReconnect(ErrorCodes.WEBSOCKET_ERROR); }
            };

            if (XiaozhiConfig.BYPASS_SSL_VALIDATION && "wss".equalsIgnoreCase(serverUri.getScheme())) {
                webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
            }
            webSocketClient.connect();
        } catch (Exception e) { Log.e(TAG, "连接错误", e); }
    }

    // 核心修改：官方协议握手包
    private void sendHelloMessage() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "hello");
            message.put("version", 3);
            message.put("transport", "websocket");
            JSONObject audioParams = new JSONObject();
            audioParams.put("format", "pcm");
            audioParams.put("sample_rate", 16000);
            audioParams.put("channels", 1);
            audioParams.put("frame_duration", 60);
            message.put("audio_params", audioParams);
            message.put("mac", deviceFingerprint.getMacAddress());

            webSocketClient.send(message.toString());
            Log.i(TAG, "已发送官方握手包");
        } catch (Exception e) { Log.e(TAG, "握手发送失败", e); }
    }

    // 核心修改：发送二进制音频流
    private void sendAudioToServer(byte[] audioData, int sampleRate, int channels) {
        if (webSocketClient == null || !webSocketClient.isOpen()) return;
        try {
            Log.i(TAG, "发送原始二进制音频: " + audioData.length + " bytes");
            webSocketClient.send(audioData);
        } catch (Exception e) { Log.e(TAG, "音频发送失败", e); }
    }

    private void handleMessage(String message) { /* 同原逻辑 */ }
    private void handleTTSMessage(JSONObject json) { /* 同原逻辑 */ }
    
    @Override
    public IBinder onBind(Intent intent) { return binder; }
    
    private void scheduleReconnect(final int errorCode) { /* 同原逻辑 */ }
    private void cancelRetries() { /* 同原逻辑 */ }
    public boolean isConnected() { return webSocketClient != null && webSocketClient.isOpen(); }
    @Override
    public void onDestroy() { super.onDestroy(); }
}