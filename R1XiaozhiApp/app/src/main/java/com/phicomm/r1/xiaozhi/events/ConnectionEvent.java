package com.phicomm.r1.xiaozhi.events;

/**
 * Event broadcast when connection status changes
 * Includes pairing success/failure
 */
public class ConnectionEvent {
    public final boolean connected;
    public final String message;
    public final long timestamp;
    
    public ConnectionEvent(boolean connected, String message) {
        this.connected = connected;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "ConnectionEvent{" +
                "connected=" + connected +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}