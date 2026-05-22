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
import com.phicomm.r1.xiaozhi.core.XiaozhiCore;
import com.phicomm.r1.xiaozhi.events.ConnectionEvent;
import com.phicomm.r1.xiaozhi.events.MessageReceivedEvent;
import com.phicomm.r1.xiaozhi.util.TrustAllCertificates;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class XiaozhiConnectionService extends Service {

    private static final String TAG = "XiaozhiConnection";
    private static final int MAX_RETRIES = 5;
    private static final int NOTIFICATION_ID = 1001;
    private static final int RECONNECT_DELAY_MS = 3000;

    private volatile WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private volatile ConnectionListener connectionListener;
    private XiaozhiCore core;
    private EventBus eventBus;
    private DeviceActivator deviceActivator;
    private DeviceFingerprint deviceFingerprint;
    private Handler retryHandler;
    private int retryCount = 0;
    private volatile boolean isConnecting = false;

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
        // Always go through activation check — both cloud and self-hosted proxy need a real token
        ensureConnected();
        return START_STICKY;
    }

    /**
     * Ensure we have a valid token and are connected.
     * The self-hosted URL (ws://192.168.1.15:12000/websocket) is a transparent nginx proxy
     * to api.tenclass.net, so it still requires the same Bearer token as the cloud URL.
     */
    private void ensureConnected() {
        String token = deviceFingerprint.getAccessToken();

        if (token == null) {
            Log.i(TAG, "No token found, starting activation");
            startActivationFlow();
            return;
        }

        if (deviceFingerprint.isTokenExpired()) {
            Log.w(TAG, "Token expired, re-activating to refresh");
            if (deviceActivator != null && deviceActivator.isActivating()) {
                Log.d(TAG, "Activation already in progress, skipping");
                return;
            }
            deviceFingerprint.setActivationStatus(false);
            startActivationFlow();
            return;
        }

        connect();
    }

    private void startActivationFlow() {
        deviceActivator.setListener(new DeviceActivator.ActivationListener() {
            @Override
            public void onActivationStarted(String verificationCode) {
                Log.i(TAG, "需要激活，验证码: " + verificationCode);
                ConnectionListener l = connectionListener;
                if (l != null) l.onActivationRequired(verificationCode);
            }

            @Override
            public void onActivationProgress(int attempt, int maxAttempts) {
                ConnectionListener l = connectionListener;
                if (l != null) l.onActivationProgress(attempt, maxAttempts);
            }

            @Override
            public void onActivationSuccess(String accessToken) {
                Log.i(TAG, "激活成功，开始连接");
                ConnectionListener l = connectionListener;
                if (l != null) l.onPairingSuccess();
                connect();
            }

            @Override
            public void onActivationFailed(String error) {
                Log.e(TAG, "激活失败: " + error);
                ConnectionListener l = connectionListener;
                if (l != null) l.onPairingFailed(error);
            }
        });
        deviceActivator.startActivation();
    }

    public void connect() {
        if (isConnecting) {
            Log.d(TAG, "Already connecting, skipping");
            return;
        }
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.d(TAG, "Already connected, skipping");
            return;
        }

        // Use stored token (not getValidAccessToken which rejects expired tokens —
        // let the server decide if the token is still acceptable)
        String token = deviceFingerprint.getAccessToken();
        if (token == null) {
            Log.e(TAG, "No access token, triggering activation");
            startActivationFlow();
            return;
        }
        connectWithToken(token);
    }

    private void connectWithToken(final String accessToken) {
        isConnecting = true;
        try {
            XiaozhiConfig config = new XiaozhiConfig(this);
            URI serverUri = new URI(config.getActiveUrl());
            Map<String, String> headers = new HashMap<>();
            // Always send Authorization — the self-hosted proxy transparently forwards it to api.tenclass.net
            headers.put("Authorization", "Bearer " + accessToken);

            final WebSocketClient client = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake h) {
                    Log.i(TAG, "连接成功，准备握手");
                    isConnecting = false;
                    retryCount = 0; // reset on successful open so MAX_RETRIES counts from here
                    sendHelloMessage(this);
                }

                @Override
                public void onMessage(String message) { handleMessage(message); }

                @Override
                public void onMessage(java.nio.ByteBuffer bytes) { handleBinaryMessage(bytes); }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "连接关闭: code=" + code + " reason='" + reason + "' remote=" + remote);
                    isConnecting = false;
                    ConnectionListener l = connectionListener;
                    if (l != null) l.onDisconnected();

                    if (!remote) return; // local close, don't reconnect

                    if (code == 4001 || code == 4003) {
                        // Explicit auth rejection — re-activate
                        Log.w(TAG, "Token被服务器拒绝(code=" + code + ")，重新激活");
                        deviceFingerprint.setActivationStatus(false);
                        startActivationFlow();
                    } else if (code == 1000 && retryCount == 0) {
                        // Server closed immediately after hello with code=1000:
                        // this means the token was rejected (server uses 1000 instead of 4001).
                        // Only treat first occurrence as auth failure to avoid infinite re-activation.
                        Log.w(TAG, "服务器立即关闭连接(code=1000)，可能token无效，重新激活");
                        deviceFingerprint.setActivationStatus(false);
                        startActivationFlow();
                    } else {
                        scheduleReconnect();
                    }
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
            Log.i(TAG, "已发送握手包: " + message.toString());
        } catch (Exception e) {
            Log.e(TAG, "握手发送失败", e);
        }
    }

    private void handleMessage(String message) {
        Log.d(TAG, "收到消息: " + message);
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            switch (type) {
                case "hello":
                    Log.i(TAG, "握手完成，连接就绪");
                    retryCount = 0;
                    ConnectionListener l = connectionListener;
                    if (l != null) l.onConnected();
                    eventBus.post(new ConnectionEvent(true, "WebSocket connected"));
                    break;

                case "tts":
                    handleTTSMessage(json);
                    break;

                case "stt":
                    String sttText = json.optString("text", "");
                    Log.i(TAG, "识别结果: " + sttText);
                    ConnectionListener sl = connectionListener;
                    if (sl != null) sl.onMessage(sttText);
                    eventBus.post(new MessageReceivedEvent(json));
                    break;

                case "llm":
                    Log.d(TAG, "LLM: " + json.optString("text", ""));
                    break;

                default:
                    Log.d(TAG, "未知消息类型: " + type + " 内容: " + message);
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
                Log.i(TAG, "TTS 文本: " + json.optString("text", ""));
                break;
            default:
                break;
        }
    }

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

    private void scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            Log.w(TAG, "重连次数已达上限(" + MAX_RETRIES + ")，60秒后重置重试");
            retryCount = 0;
            ConnectionListener l = connectionListener;
            if (l != null)
                l.onError("Connection failed after " + MAX_RETRIES + " retries, will retry in 60s");
            // Schedule a longer-interval retry so the service never goes permanently dormant
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isConnected()) ensureConnected();
                }
            }, 60000);
            return;
        }
        retryCount++;
        int delay = Math.min(RECONNECT_DELAY_MS * retryCount, 30000);
        Log.i(TAG, "将在 " + delay + "ms 后重连 (第 " + retryCount + "/" + MAX_RETRIES + " 次)");
        retryHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnected()) ensureConnected();
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
        if (webSocketClient != null) webSocketClient.close();
    }

    public void resetActivation() {
        if (deviceActivator != null) deviceActivator.resetActivation();
    }

    @Override
    public void onDestroy() {
        retryHandler.removeCallbacksAndMessages(null);
        WebSocketClient ws = webSocketClient;
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing WebSocket", e);
            }
        }
        super.onDestroy();
    }
}
