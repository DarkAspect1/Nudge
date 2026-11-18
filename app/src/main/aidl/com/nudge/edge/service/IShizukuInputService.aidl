package com.nudge.edge.service;

import com.nudge.edge.service.IShizukuInputCallback;

interface IShizukuInputService {
    void registerCallback(IShizukuInputCallback callback);
    void unregisterCallback(IShizukuInputCallback callback);
    void startMonitoring();
    void stopMonitoring();
    String getKeyLabel(String deviceName, int scanCode);
    void destroy();
    boolean setDndEnabled(boolean enabled);
}
