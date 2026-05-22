package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.activation.DeviceFingerprint;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple HTTP Server to expose device status and remote control via REST API
 * Port is read from XiaozhiConfig (default 8088)
 *
 * Endpoints:
 *   GET  /pairing          - pairing code and device ID
 *   GET  /pairing-code     - alias for /pairing
 *   GET  /status           - pairing and connection status
 *   GET  /start            - start voice recognition and connection services
 *   GET  /stop             - stop voice recognition and connection services
 *   GET  /config           - current configuration
 *   POST /reset-pairing    - reset pairing and return new code
 *   POST /reset            - alias for /reset-pairing
 */
public class HTTPServerService extends Service {

    private static final String TAG = "HTTPServer";

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            startServer();
        }
        return START_STICKY;
    }

    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int port = new XiaozhiConfig(HTTPServerService.this).getHttpServerPort();
                try {
                    serverSocket = new ServerSocket(port);
                    isRunning = true;
                    Log.i(TAG, "HTTP Server started on port " + port);

                    while (isRunning && !serverSocket.isClosed()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            handleClient(clientSocket);
                        } catch (IOException e) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting client: " + e.getMessage());
                            }
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Failed to start server on port " + port + ": " + e.getMessage(), e);
                } finally {
                    isRunning = false;
                }
            }
        });
        serverThread.start();
    }

    private void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = reader.readLine();
            if (requestLine == null) return;

            Log.d(TAG, "Request: " + requestLine);

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // skip headers
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(writer, 400, "Bad Request");
                return;
            }

            String method = parts[0];
            // strip query string for routing
            String path = parts[1].split("\\?")[0];

            if ("GET".equals(method) && ("/pairing".equals(path) || "/pairing-code".equals(path))) {
                servePairingCode(writer);
            } else if ("GET".equals(method) && "/status".equals(path)) {
                serveStatus(writer);
            } else if ("GET".equals(method) && "/start".equals(path)) {
                serveStart(writer);
            } else if ("GET".equals(method) && "/stop".equals(path)) {
                serveStop(writer);
            } else if ("GET".equals(method) && "/config".equals(path)) {
                serveConfig(writer);
            } else if ("POST".equals(method) && ("/reset-pairing".equals(path) || "/reset".equals(path))) {
                serveResetPairing(writer);
            } else {
                sendResponse(writer, 404, "Not Found");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error handling client: " + e.getMessage());
        }
    }

    /** GET /pairing, /pairing-code */
    private void servePairingCode(PrintWriter writer) {
        try {
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            boolean isPaired = PairingCodeGenerator.isPaired(this);
            DeviceFingerprint fp = DeviceFingerprint.getInstance(this);
            String verificationCode = isPaired ? null : fp.getVerificationCode();

            JSONObject response = new JSONObject();
            response.put("device_id", deviceId);
            response.put("paired", isPaired);
            if (verificationCode != null) {
                response.put("verification_code", verificationCode);
            }

            sendJsonResponse(writer, 200, response.toString());
            Log.d(TAG, "Served pairing info, paired=" + isPaired);

        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    /** GET /status */
    private void serveStatus(PrintWriter writer) {
        try {
            boolean isPaired = PairingCodeGenerator.isPaired(this);
            String deviceId = PairingCodeGenerator.getDeviceId(this);

            JSONObject response = new JSONObject();
            response.put("status", isPaired ? "paired" : "not_paired");
            response.put("paired", isPaired);
            response.put("device_id", deviceId);

            sendJsonResponse(writer, 200, response.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    /** GET /start — start VoiceRecognitionService and XiaozhiConnectionService */
    private void serveStart(PrintWriter writer) {
        startService(new Intent(this, VoiceRecognitionService.class));
        startService(new Intent(this, XiaozhiConnectionService.class));
        Log.i(TAG, "Services started via HTTP");
        try {
            JSONObject response = new JSONObject();
            response.put("status", "started");
            sendJsonResponse(writer, 200, response.toString());
        } catch (JSONException e) {
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    /** GET /stop — stop VoiceRecognitionService and XiaozhiConnectionService */
    private void serveStop(PrintWriter writer) {
        stopService(new Intent(this, VoiceRecognitionService.class));
        stopService(new Intent(this, XiaozhiConnectionService.class));
        Log.i(TAG, "Services stopped via HTTP");
        try {
            JSONObject response = new JSONObject();
            response.put("status", "stopped");
            sendJsonResponse(writer, 200, response.toString());
        } catch (JSONException e) {
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    /** GET /config — return current XiaozhiConfig as JSON */
    private void serveConfig(PrintWriter writer) {
        try {
            sendJsonResponse(writer, 200, new XiaozhiConfig(this).exportConfig());
        } catch (Exception e) {
            Log.e(TAG, "Failed to serve config: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    /** POST /reset-pairing, /reset */
    private void serveResetPairing(PrintWriter writer) {
        PairingCodeGenerator.resetPairing(this);
        String newCode = PairingCodeGenerator.getPairingCode(this);
        Log.i(TAG, "Pairing reset via HTTP, new code: " + newCode);
        try {
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("new_pairing_code", newCode);
            response.put("message", "Pairing reset successfully");
            sendJsonResponse(writer, 200, response.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }

    private void sendResponse(PrintWriter writer, int statusCode, String statusMessage) {
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Content-Type: text/plain");
        writer.println("Connection: close");
        writer.println();
        writer.println(statusMessage);
    }

    private void sendJsonResponse(PrintWriter writer, int statusCode, String json) {
        String statusMessage = statusCode == 200 ? "OK" : "Error";
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Content-Type: application/json");
        writer.println("Content-Length: " + json.getBytes().length);
        writer.println("Connection: close");
        writer.println();
        writer.println(json);
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
        Log.i(TAG, "HTTP Server stopped");
    }

    private void stopServer() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket: " + e.getMessage());
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}
