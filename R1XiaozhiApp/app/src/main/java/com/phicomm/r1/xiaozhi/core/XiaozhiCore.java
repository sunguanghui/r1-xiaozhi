package com.phicomm.r1.xiaozhi.core;

import android.content.Context;
import android.util.Log;

import com.phicomm.r1.xiaozhi.events.StateChangedEvent;
import com.phicomm.r1.xiaozhi.service.AudioPlaybackService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;

/**
 * Core singleton following the py-xiaozhi Application class model
 * Manages centralized state and coordination between services
 * 
 * Thread-safe singleton với double-checked locking
 * 
 * Usage:
 * XiaozhiCore core = XiaozhiCore.getInstance();
 * core.initialize(context);
 * core.setDeviceState(DeviceState.LISTENING);
 */
public class XiaozhiCore {
    
    private static final String TAG = "XiaozhiCore";
    
    // Thread-safe singleton
    private static volatile XiaozhiCore instance;
    private static final Object lock = new Object();
    
    // Event bus
    private final EventBus eventBus;
    
    // Device state (volatile for visibility across threads)
    private volatile DeviceState deviceState = DeviceState.IDLE;
    private volatile ListeningMode listeningMode = ListeningMode.AUTO_STOP;
    private volatile boolean keepListening = false;
    private volatile boolean aecEnabled = true;

    // Service references (volatile so threads always see the latest binding)
    private volatile XiaozhiConnectionService connectionService;
    private volatile AudioPlaybackService audioService;
    private volatile VoiceRecognitionService voiceService;
    private volatile LEDControlService ledService;
    
    // Application context
    private Context applicationContext;
    
    /**
     * Private constructor để enforce singleton pattern
     */
    private XiaozhiCore() {
        this.eventBus = new EventBus();
        Log.i(TAG, "XiaozhiCore instance created");
    }
    
    /**
     * Get singleton instance (thread-safe double-checked locking)
     * 
     * @return XiaozhiCore singleton instance
     */
    public static XiaozhiCore getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new XiaozhiCore();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize with application context
     * Should be called in Application.onCreate()
     *
     * @param context Application context
     */
    public void initialize(Context context) {
        if (this.applicationContext == null) {
            this.applicationContext = context.getApplicationContext();
            Log.i(TAG, "XiaozhiCore initialized with context");
            Log.i(TAG, "Initial state: " + getStateSnapshot());
        }
    }
    
    // ==================== State Management ====================
    
    /**
     * Set device state (thread-safe)
     * Broadcasts StateChangedEvent if state changes
     * 
     * @param newState New device state
     */
    public synchronized void setDeviceState(DeviceState newState) {
        if (newState == null) {
            Log.w(TAG, "Attempted to set null device state");
            return;
        }
        
        if (this.deviceState != newState) {
            DeviceState oldState = this.deviceState;
            this.deviceState = newState;
            
            Log.i(TAG, "State changed: " + oldState + " -> " + newState);
            
            // Broadcast event (on main thread)
            eventBus.post(new StateChangedEvent(oldState, newState));
        }
    }
    
    /**
     * Get current device state
     * 
     * @return Current DeviceState
     */
    public DeviceState getDeviceState() {
        return deviceState;
    }
    
    /**
     * Check if device is in IDLE state
     */
    public boolean isIdle() {
        return deviceState == DeviceState.IDLE;
    }
    
    /**
     * Check if device is in LISTENING state
     */
    public boolean isListening() {
        return deviceState == DeviceState.LISTENING;
    }
    
    /**
     * Check if device is in SPEAKING state
     */
    public boolean isSpeaking() {
        return deviceState == DeviceState.SPEAKING;
    }
    
    // ==================== Listening Mode Management ====================
    
    /**
     * Set listening mode
     * 
     * @param mode New listening mode
     */
    public synchronized void setListeningMode(ListeningMode mode) {
        if (mode == null) {
            Log.w(TAG, "Attempted to set null listening mode");
            return;
        }
        
        if (this.listeningMode != mode) {
            Log.i(TAG, "Listening mode changed: " + this.listeningMode + " -> " + mode);
            this.listeningMode = mode;
        }
    }
    
    /**
     * Get current listening mode
     * 
     * @return Current ListeningMode
     */
    public ListeningMode getListeningMode() {
        return listeningMode;
    }
    
    /**
     * Set keep listening flag
     * When true: automatically continues listening after TTS completes
     * When false: returns to IDLE after TTS completes
     * 
     * @param keepListening Keep listening flag
     */
    public void setKeepListening(boolean keepListening) {
        this.keepListening = keepListening;
        Log.d(TAG, "Keep listening: " + keepListening);
    }
    
    /**
     * Check if keep listening is enabled
     * 
     * @return true if keep listening is enabled
     */
    public boolean isKeepListening() {
        return keepListening;
    }
    
    /**
     * Set AEC (Acoustic Echo Cancellation) enabled
     * Affects the default listening mode
     * 
     * @param enabled AEC enabled flag
     */
    public void setAecEnabled(boolean enabled) {
        this.aecEnabled = enabled;
        Log.d(TAG, "AEC enabled: " + enabled);
        
        // Auto update listening mode based on AEC
        if (enabled) {
            setListeningMode(ListeningMode.REALTIME);
        } else {
            setListeningMode(ListeningMode.AUTO_STOP);
        }
    }
    
    /**
     * Check if AEC is enabled
     * 
     * @return true if AEC is enabled
     */
    public boolean isAecEnabled() {
        return aecEnabled;
    }
    
    // ==================== Service References ====================
    
    /**
     * Set connection service reference
     * Called by XiaozhiConnectionService in onCreate()
     */
    public void setConnectionService(XiaozhiConnectionService service) {
        this.connectionService = service;
        Log.d(TAG, "Connection service registered");
    }
    
    /**
     * Get connection service reference
     */
    public XiaozhiConnectionService getConnectionService() {
        return connectionService;
    }
    
    /**
     * Set audio service reference
     * Called by AudioPlaybackService in onCreate()
     */
    public void setAudioService(AudioPlaybackService service) {
        this.audioService = service;
        Log.d(TAG, "Audio service registered");
    }
    
    /**
     * Get audio service reference
     */
    public AudioPlaybackService getAudioService() {
        return audioService;
    }
    
    /**
     * Set voice recognition service reference
     * Called by VoiceRecognitionService in onCreate()
     */
    public void setVoiceService(VoiceRecognitionService service) {
        this.voiceService = service;
        Log.d(TAG, "Voice service registered");
    }
    
    /**
     * Get voice recognition service reference
     */
    public VoiceRecognitionService getVoiceService() {
        return voiceService;
    }
    
    /**
     * Set LED control service reference
     * Called by LEDControlService in onCreate()
     */
    public void setLedService(LEDControlService service) {
        this.ledService = service;
        Log.d(TAG, "LED service registered");
    }
    
    /**
     * Get LED control service reference
     */
    public LEDControlService getLedService() {
        return ledService;
    }
    
    // ==================== EventBus Access ====================
    
    /**
     * Get EventBus instance
     * Used to register/unregister listeners and post events
     * 
     * @return EventBus instance
     */
    public EventBus getEventBus() {
        return eventBus;
    }
    
    // ==================== Context Access ====================
    
    /**
     * Get application context
     * 
     * @return Application context
     */
    public Context getApplicationContext() {
        return applicationContext;
    }
    
    // ==================== State Snapshot ====================
    
    /**
     * Get snapshot of the entire state (for debugging)
     * 
     * @return String representation of current state
     */
    public String getStateSnapshot() {
        return "XiaozhiCore{" +
                "deviceState=" + deviceState +
                ", listeningMode=" + listeningMode +
                ", keepListening=" + keepListening +
                ", aecEnabled=" + aecEnabled +
                ", connectionService=" + (connectionService != null ? "bound" : "null") +
                ", audioService=" + (audioService != null ? "bound" : "null") +
                ", voiceService=" + (voiceService != null ? "bound" : "null") +
                ", ledService=" + (ledService != null ? "bound" : "null") +
                ", eventListeners=" + getEventListenerStats() +
                '}';
    }
    
    /**
     * Get statistics về event listeners
     */
    private String getEventListenerStats() {
        return "StateChanged:" + eventBus.getListenerCount(StateChangedEvent.class);
    }
    
    // ==================== Shutdown ====================
    
    /**
     * Cleanup resources on shutdown
     * Should be called in Application.onTerminate() or when exiting the app
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down XiaozhiCore...");
        
        // Clear event bus
        eventBus.clear();
        
        // Clear service references
        connectionService = null;
        audioService = null;
        voiceService = null;
        ledService = null;
        
        // Reset state
        deviceState = DeviceState.IDLE;
        keepListening = false;
        
        Log.i(TAG, "XiaozhiCore shutdown complete");
    }
}