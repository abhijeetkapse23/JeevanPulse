package com.jeevanpulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Periodic battery check: warns loved ones once if the phone is about to die. */
public class BatteryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        Pulse.checkBattery(context, pr::finish);
    }
}
