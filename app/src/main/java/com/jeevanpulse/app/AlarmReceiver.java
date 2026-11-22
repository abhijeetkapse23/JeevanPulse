package com.jeevanpulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // goAsync keeps the app awake until WhatsApp/SMS sending has finished.
        final PendingResult pr = goAsync();
        Pulse.onAlarm(context, pr::finish);
    }
}
