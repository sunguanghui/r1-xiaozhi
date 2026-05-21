package com.phicomm.r1.xiaozhi.core;

/**
 * Device states from py-xiaozhi
 * Defines the 3 main states of the device
 */
public enum DeviceState {
    /**
     * Idle state - not active
     */
    IDLE("idle"),

    /**
     * Listening - recording and recognizing voice input
     */
    LISTENING("listening"),

    /**
     * Speaking - playing TTS audio
     */
    SPEAKING("speaking");
    
    private final String value;
    
    DeviceState(String value) {
        this.value = value;
    }
    
    /**
     * Get the string value of the state
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Convert from string to DeviceState
     */
    public static DeviceState fromString(String value) {
        if (value == null) {
            return IDLE;
        }
        
        for (DeviceState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return IDLE;
    }
    
    @Override
    public String toString() {
        return value;
    }
}