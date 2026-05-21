package com.phicomm.r1.xiaozhi.core;

/**
 * Listening modes from py-xiaozhi
 * Defines the different listening modes
 */
public enum ListeningMode {
    /**
     * MANUAL: Push-to-talk mode
     * User holds button to speak, releases to stop
     * Use case: Presenter, public speaking
     */
    MANUAL("manual"),

    /**
     * AUTO_STOP: Automatically stops when silence is detected
     * Suitable for normal conversation
     * Use case: Normal conversation
     */
    AUTO_STOP("auto_stop"),

    /**
     * REALTIME: Continuous listening with AEC (Acoustic Echo Cancellation)
     * Always listening, even during TTS playback
     * Requires: Hardware with AEC support
     * Use case: Always-on voice assistant
     */
    REALTIME("realtime");
    
    private final String value;
    
    ListeningMode(String value) {
        this.value = value;
    }
    
    /**
     * Get the string value of the mode
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Convert from string to ListeningMode
     */
    public static ListeningMode fromString(String value) {
        if (value == null) {
            return AUTO_STOP;
        }
        
        for (ListeningMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return AUTO_STOP;
    }
    
    @Override
    public String toString() {
        return value;
    }
}