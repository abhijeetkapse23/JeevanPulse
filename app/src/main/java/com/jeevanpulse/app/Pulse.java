package com.jeevanpulse.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * All JeevanPulse logic and storage.
 *
 * Cycle:
 *  - After an "I'm OK" (or Start), the next prompt is scheduled after the check-in interval.
 *  - A prompt shows a notification with an "I'm OK" button and waits for the retry gap.
 *  - If the gap passes with no tap, that is a miss:
 *      miss 1          -> SMS to every loved one
 *      miss 3, 6, 9... -> phone call to the emergency contact (or a 112 suggestion if none)
 *    and the prompt is shown again.
 *  - Tapping "I'm OK" at any point logs it, resets the counter, and (if loved ones were
 *    alerted) sends them an "all OK" SMS.
 */
public final class Pulse {

    static final String PREFS = "jeevanpulse";
    static final String CH_PROMPT = "pulse_prompt";
    static final String CH_ALERT = "pulse_alert";
    static final int NOTIF_PROMPT = 1;
    static final int NOTIF_ALERT = 2;
    static final String ACTION_ALARM = "com.jeevanpulse.app.ALARM";
    static final String ACTION_OK = "com.jeevanpulse.app.OK";
    static final int CALL_AFTER = 3;

    /** {check-in interval minutes, retry gap minutes} for each mode in R.array.modes. */
    static final int[][] MODES = {{2, 2}, {12 * 60, 30}, {24 * 60, 30}};

    private Pulse() {}

    // ---------- storage ----------

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean active(Context c) { return sp(c).getBoolean("active", false); }
    static boolean awaiting(Context c) { return sp(c).getBoolean("awaiting", false); }
    static int missed(Context c) { return sp(c).getInt("missed", 0); }
    static long nextAt(Context c) { return sp(c).getLong("nextAt", 0L); }
    static long lastOk(Context c) { return sp(c).getLong("lastOk", 0L); }
    static String name(Context c) { return sp(c).getString("name", ""); }

    static int mode(Context c) {
        int m = sp(c).getInt("mode", 0);
        return (m < 0 || m >= MODES.length) ? 0 : m;
    }

    static long intervalMs(Context c) { return MODES[mode(c)][0] * 60_000L; }
    static long retryMs(Context c) { return MODES[mode(c)][1] * 60_000L; }

    static String displayName(Context c) {
        String n = name(c).trim();
        return n.isEmpty() ? c.getString(R.string.default_name) : n;
    }

    static JSONArray contacts(Context c) {
        try {
            return new JSONArray(sp(c).getString("contacts", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void saveContacts(Context c, JSONArray a) {
        sp(c).edit().putString("contacts", a.toString()).apply();
    }

    static JSONObject emergencyContact(Context c) {
        JSONArray a = contacts(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optBoolean("emergency", false)) return o;
        }
        return null;
    }

    static JSONArray log(Context c) {
        try {
            return new JSONArray(sp(c).getString("log", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void addLog(Context c, String text) {
        JSONArray old = log(c);
        JSONArray a = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("x", text);
            a.put(o);
            for (int i = 0; i < old.length() && i < 80; i++) a.put(old.get(i));
        } catch (JSONException ignored) { }
        sp(c).edit().putString("log", a.toString()).apply();
    }

    static String time(Context c, long t) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(t));
    }

    // ---------- actions ----------

    static void start(Context c) {
        channels(c);
        sp(c).edit()
                .putBoolean("active", true)
                .putBoolean("awaiting", false)
                .putBoolean("alerted", false)
                .putInt("missed", 0)
                .apply();
        cancelNotifs(c);
        schedule(c, intervalMs(c));
        String[] modes = c.getResources().getStringArray(R.array.modes);
        addLog(c, c.getString(R.string.log_started, modes[mode(c)]));
    }

    static void stop(Context c) {
        sp(c).edit().putBoolean("active", false).putBoolean("awaiting", false).putInt("missed", 0).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent(c));
        cancelNotifs(c);
        addLog(c, c.getString(R.string.log_stopped));
    }

    /** User tapped "I'm OK" (in the app or on the notification). */
    static void ok(Context c) {
        boolean alerted = sp(c).getBoolean("alerted", false);
        sp(c).edit()
                .putBoolean("awaiting", false)
                .putBoolean("alerted", false)
                .putInt("missed", 0)
                .putLong("lastOk", System.currentTimeMillis())
                .apply();
        cancelNotifs(c);
        if (alerted) {
            sendSmsToAll(c, c.getString(R.string.sms_ok, displayName(c)));
            addLog(c, c.getString(R.string.log_ok_told));
        } else {
            addLog(c, c.getString(R.string.log_ok));
        }
        if (active(c)) schedule(c, intervalMs(c));
    }

    /** Called by the alarm. */
    static void onAlarm(Context c) {
        if (!active(c)) return;
        channels(c);
        int missed = missed(c);
        if (awaiting(c)) {
            missed++;
            sp(c).edit().putInt("missed", missed).apply();
            addLog(c, c.getString(R.string.log_missed, missed, CALL_AFTER));
            if (missed == 1) {
                int sent = sendSmsToAll(c, c.getString(R.string.sms_alert, displayName(c)));
                if (sent > 0) sp(c).edit().putBoolean("alerted", true).apply();
            }
            if (missed % CALL_AFTER == 0) {
                callEmergency(c, missed);
            }
        }
        showPrompt(c, missed);
        sp(c).edit().putBoolean("awaiting", true).apply();
        schedule(c, retryMs(c));
    }

    /** After a reboot or app update, alarms are gone; set the next one again. */
    static void restore(Context c) {
        if (!active(c)) return;
        long at = nextAt(c);
        long now = System.currentTimeMillis();
        schedule(c, at > now ? at - now : 60_000L);
    }

    // ---------- scheduling ----------

    static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALARM);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c, long delayMs) {
        long at = System.currentTimeMillis() + delayMs;
        sp(c).edit().putLong("nextAt", at).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = alarmIntent(c);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    // ---------- notifications ----------

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel p = new NotificationChannel(CH_PROMPT,
                c.getString(R.string.ch_prompt), NotificationManager.IMPORTANCE_HIGH);
        p.enableVibration(true);
        nm.createNotificationChannel(p);
        NotificationChannel a = new NotificationChannel(CH_ALERT,
                c.getString(R.string.ch_alert), NotificationManager.IMPORTANCE_HIGH);
        a.enableVibration(true);
        nm.createNotificationChannel(a);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 2, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void showPrompt(Context c, int missed) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Intent okI = new Intent(c, OkReceiver.class).setAction(ACTION_OK);
        PendingIntent okPi = PendingIntent.getBroadcast(c, 1, okI,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = missed == 0
                ? c.getString(R.string.n_prompt_text)
                : c.getString(R.string.n_prompt_missed, missed, CALL_AFTER);
        Notification n = new Notification.Builder(c, CH_PROMPT)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFF28C28)
                .setContentTitle(c.getString(R.string.n_prompt_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openApp(c))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_stat),
                        c.getString(R.string.n_ok), okPi).build())
                .setAutoCancel(false)
                .build();
        nm.notify(NOTIF_PROMPT, n);
    }

    static void showAlert(Context c, String title, String text, PendingIntent tap) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFD64545)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ALERT, n);
    }

    static void cancelNotifs(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(NOTIF_PROMPT);
        nm.cancel(NOTIF_ALERT);
    }

    // ---------- SMS & call ----------

    static boolean granted(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    static int sendSmsToAll(Context c, String msg) {
        JSONArray a = contacts(c);
        if (a.length() == 0 || !granted(c, Manifest.permission.SEND_SMS)) {
            addLog(c, c.getString(R.string.log_sms_none));
            return 0;
        }
        SmsManager sm = Build.VERSION.SDK_INT >= 31
                ? c.getSystemService(SmsManager.class)
                : SmsManager.getDefault();
        int sent = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String phone = o.optString("phone", "");
            if (phone.isEmpty()) continue;
            try {
                ArrayList<String> parts = sm.divideMessage(msg);
                sm.sendMultipartTextMessage(phone, null, parts, null, null);
                sent++;
            } catch (Exception e) {
                addLog(c, "SMS error: " + e.getMessage());
            }
        }
        addLog(c, c.getString(R.string.log_sms, sent));
        return sent;
    }

    static void callEmergency(Context c, int missed) {
        JSONObject who = emergencyContact(c);
        if (who == null) {
            // No emergency contact: only now do we suggest 112 (opens the dialer, never auto-dials).
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 3, dial,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            showAlert(c, c.getString(R.string.n_no_contact_title),
                    c.getString(R.string.n_no_contact_text, missed), pi);
            addLog(c, c.getString(R.string.log_no_emergency));
            return;
        }
        String nm = who.optString("name", "");
        String phone = who.optString("phone", "");
        boolean placed = false;
        if (granted(c, Manifest.permission.CALL_PHONE)) {
            try {
                TelecomManager tm = c.getSystemService(TelecomManager.class);
                if (tm != null) {
                    tm.placeCall(Uri.fromParts("tel", phone, null), new Bundle());
                    placed = true;
                }
            } catch (Exception ignored) { }
            if (!placed) {
                try {
                    Intent call = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(call);
                    placed = true;
                } catch (Exception ignored) { }
            }
        }
        if (placed) {
            addLog(c, c.getString(R.string.log_call, nm));
            showAlert(c, c.getString(R.string.n_calling_title, nm),
                    c.getString(R.string.n_calling_text, missed), openApp(c));
        } else {
            // Android blocked the automatic call: give a one-tap call instead.
            String action = granted(c, Manifest.permission.CALL_PHONE) ? Intent.ACTION_CALL : Intent.ACTION_DIAL;
            Intent call = new Intent(action, Uri.fromParts("tel", phone, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 4, call,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            showAlert(c, c.getString(R.string.n_call_fallback, nm),
                    c.getString(R.string.n_calling_text, missed), pi);
            addLog(c, c.getString(R.string.log_call_blocked, nm));
        }
    }
}
