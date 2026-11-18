package com.nudge.edge.service;

oneway interface IShizukuInputCallback {
    void onInputEvent(String deviceName, int scanCode, int value, long timestampMs);
}
