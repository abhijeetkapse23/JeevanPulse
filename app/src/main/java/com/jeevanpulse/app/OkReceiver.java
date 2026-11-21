package com.jeevanpulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Handles the "I'm OK" button on the notification, without opening the app. */
public class OkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Pulse.ok(context);
        Toast.makeText(context, R.string.toast_ok, Toast.LENGTH_SHORT).show();
    }
}
