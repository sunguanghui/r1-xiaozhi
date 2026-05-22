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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class XiaozhiConnectionService extends Service {

    private static final String TAG = "XiaozhiConnection";
    private static final int MAX_RETRIES = 5;
    private static final int NOTIFICATION_ID = 1001;
    private static final int RECONNECT_DELAY_MS = 3000;

    private WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private ConnectionListener connectionListener;
    private XiaozhiCore core;
    private EventBus eventBus;
    private DeviceActivator deviceActivator;
    private DeviceFingerprint deviceFingerprint;
    private Handler retryHandler;
    private int retryCount = 0;
    private boolean isConnecting = false;

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
        Intent notificationIntent = new Intent(this, com.phicomm.r1.xiaozhi.ui.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
            .setContentTitle("Xiaozhi Service")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build();
        startForeground(NOTIFICATION_ID, notification);

        retryHandler = new Handler();
        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        deviceFingerprint = DeviceFingerprint.getInstance(this);
        deviceActivator = new DeviceActivator(this);
        core.setConnectionService(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "SEND_AUDIO".equals(intent.getAction())) {
            byte[] audioData = intent.getByteArrayExtra("audio_data");
            if (audioData != null) sendAudioToServer(audioData);
            return START_STICKY;
        }

        if (deviceActivator.isActivated()) {
            connect();
        } else {
            deviceActivator.startActivation();
        }
        return START_STICKY;
    }

    public void connect() {
        // Prevent duplicate connections
        if (isConnecting) {
            Log.d(TAG, "Already connecting, skipping");
            return;
        }
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.d(TAG, "Already connected, skipping");
            return;
        }

        String accessToken = deviceFingerprint.getValidAccessToken();
        if (accessToken == null) {
            Log.e(TAG, "No valid access token");
            return;
        }
        connectWithToken(accessToken);
    }

    private void connectWithToken(final String accessToken) {
        isConnecting = true;
        try {
            XiaozhiConfig config = new XiaozhiConfig(this);
            URI serverUri = new URI(config.getActiveUrl());
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);

            // FIX: capture as final local so onOpen uses THIS instance, not the field
            final WebSocketClient client = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake h) {
                    Log.i(TAG, "连接成功，准备握手");
                    isConnecting = false;
                    retryCount = 0;
                    sendHelloMessage(this);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onMessage(java.nio.ByteBuffer bytes) {
                    handleBinaryMessage(bytes);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "连接关闭: code=" + code + " reason=" + reason);
                    isConnecting = false;
                    if (connectionListener != null) connectionListener.onDisconnected();
                    if (remote) scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket错误", ex);
                    isConnecting = false;
                    scheduleReconnect();
                }
            };

            webSocketClient = client;

            if (XiaozhiConfig.BYPASS_SSL_VALIDATION && "wss".equalsIgnoreCase(serverUri.getScheme())) {
                client.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
            }
            client.connect();
            Log.i(TAG, "正在连接: " + serverUri);
        } catch (Exception e) {
            Log.e(TAG, "连接错误", e);
            isConnecting = false;
            scheduleReconnect();
        }
    }

    // FIX: pass the specific client instance instead of using the field
    private void sendHelloMessage(WebSocketClient client) {
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

            client.send(message.toString());
            Log.i(TAG, "已发送官方握手包");
        } catch (Exception e) {
            Log.e(TAG, "握手发送失败", e);
        }
    }

    // FIX: implement message handling - server hello response + TTS control messages
    private void handleMessage(String message) {
        Log.d(TAG, "收到消息: " + message);
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            switch (type) {
                case "hello":
                    // Server handshake accepted - connection is ready
                    Log.i(TAG, "握手完成，连接就绪");
                    if (connectionListener != null) connectionListener.onConnected();
                    eventBus.post(new ConnectionEvent(true, "WebSocket connected"));
                    break;

                case "tts":
                    handleTTSMessage(json);
                    break;

                case "stt":
                    String sttText = json.optString("text", "");
                    Log.i(TAG, "识别结果: " + sttText);
                    if (connectionListener != null) connectionListener.onMessage(sttText);
                    eventBus.post(new MessageReceivedEvent(json));
                    break;

                case "llm":
                    String llmText = json.optString("text", "");
                    Log.d(TAG, "LLM: " + llmText);
                    break;

                default:
                    Log.d(TAG, "未知消息类型: " + type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "消息解析失败: " + message, e);
        }
    }

    private void handleTTSMessage(JSONObject json) {
        String state = json.optString("state", "");
        switch (state) {
            case "start":
                Log.i(TAG, "TTS 开始");
                core.setDeviceState(DeviceState.SPEAKING);
                break;

            case "stop":
                Log.i(TAG, "TTS 结束");
                core.setDeviceState(DeviceState.IDLE);
                break;

            case "sentence_start":
                String text = json.optString("text", "");
                Log.i(TAG, "TTS 文本: " + text);
                break;

            default:
                break;
        }
    }

    // Handle binary audio data from server (TTS audio stream)
    private void handleBinaryMessage(java.nio.ByteBuffer bytes) {
        byte[] audioData = new byte[bytes.remaining()];
        bytes.get(audioData);
        Log.d(TAG, "收到音频数据: " + audioData.length + " bytes");

        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(AudioPlaybackService.ACTION_PLAY_DATA);
        intent.putExtra("audio_data", audioData);
        startService(intent);
    }

    private void sendAudioToServer(byte[] audioData) {
        if (webSocketClient == null || !webSocketClient.isOpen()) return;
        try {
            webSocketClient.send(audioData);
            Log.d(TAG, "发送音频: " + audioData.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "音频发送失败", e);
        }
    }

    // FIX: implement reconnect with backoff
    private void scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "重连次数已达上限");
            if (connectionListener != null) connectionListener.onError("Connection failed after " + MAX_RETRIES + " retries");
            return;
        }
        retryCount++;
        int delay = RECONNECT_DELAY_MS * retryCount;
        Log.i(TAG, "将在 " + delay + "ms 后重连 (第 " + retryCount + " 次)");
        retryHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnected()) connect();
            }
        }, delay);
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    public boolean isConnected() { return webSocketClient != null && webSocketClient.isOpen(); }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public boolean isActivated() {
        return deviceActivator != null && deviceActivator.isActivated();
    }

    public void cancelActivation() {
        if (deviceActivator != null) deviceActivator.cancelActivation();
    }

    public void disconnect() {
        retryCount = MAX_RETRIES; // prevent auto-reconnect
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    public void resetActivation() {
        if (deviceActivator != null) deviceActivator.resetActivation();
    }

    @Override
    public void onDestroy() {
        if (webSocketClient != null) webSocketClient.close();
        super.onDestroy();
    }
}
