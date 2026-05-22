package com.phicomm.r1.xiaozhi.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event broadcasting system following the py-xiaozhi model
 * Thread-safe, posts events on the main thread
 *
 * Usage:
 * // Register listener
 * eventBus.register(StateChangedEvent.class, event -> {
 *     // Handle event on main thread
 * });
 *
 * // Post event
 * eventBus.post(new StateChangedEvent(oldState, newState));
 */
public class EventBus {
    
    private static final String TAG = "EventBus";
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    
    /**
     * Register a listener for a specific event type
     * Thread-safe: can be called from any thread
     *
     * @param eventType Event class (e.g., StateChangedEvent.class)
     * @param listener Listener to be invoked when the event is posted
     */
    public <T> void register(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.computeIfAbsent(
                eventType, k -> new CopyOnWriteArrayList<>());
        eventListeners.add(listener);
        Log.d(TAG, "Registered listener for " + eventType.getSimpleName() +
              " (total: " + eventListeners.size() + ")");
    }
    
    /**
     * Unregister a listener
     * Thread-safe: can be called from any thread
     *
     * @param eventType Event class
     * @param listener Listener to unregister
     */
    public <T> void unregister(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            Log.d(TAG, "Unregistered listener for " + eventType.getSimpleName() +
                  " (remaining: " + eventListeners.size() + ")");
        }
    }
    
    /**
     * Post an event to all listeners (on the main thread)
     * Thread-safe: can be called from any thread
     *
     * @param event Event object to broadcast
     */
    public <T> void post(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            Log.d(TAG, "No listeners for " + eventType.getSimpleName());
            return;
        }
        
        Log.d(TAG, "Broadcasting " + eventType.getSimpleName() + " to " + 
              eventListeners.size() + " listeners");
        
        // Post all listeners on the main thread
        for (final EventListener listener : eventListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in event listener for " + 
                              event.getClass().getSimpleName(), e);
                    }
                }
            });
        }
    }
    
    /**
     * Post an event immediately (on the current thread)
     * WARNING: Only use this if certain you are on the main thread
     *
     * @param event Event object to broadcast
     */
    public <T> void postSync(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Broadcasting (sync) " + eventType.getSimpleName() + " to " + 
              eventListeners.size() + " listeners");
        
        for (final EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in event listener for " + 
                      event.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * Clear all listeners
     * Typically called on application shutdown
     */
    public void clear() {
        int totalListeners = 0;
        for (List<EventListener<?>> list : listeners.values()) {
            totalListeners += list.size();
        }
        listeners.clear();
        Log.d(TAG, "Cleared all listeners (total was: " + totalListeners + ")");
    }
    
    /**
     * Get the number of listeners for a given event type
     */
    public <T> int getListenerCount(Class<T> eventType) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        return eventListeners != null ? eventListeners.size() : 0;
    }
    
    /**
     * Event listener interface
     * 
     * @param <T> Event type
     */
    public interface EventListener<T> {
        /**
         * Called when an event is posted
         * ALWAYS invoked on the main thread (UI thread)
         *
         * @param event Event object
         */
        void onEvent(T event);
    }
}