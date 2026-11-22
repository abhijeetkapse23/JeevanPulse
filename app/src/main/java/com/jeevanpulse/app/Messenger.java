package com.jeevanpulse.app;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Delivers a message to every loved one.
 *
 * For each person: WhatsApp first (free CallMeBot API, if that person has set up a key and the
 * phone is online). If WhatsApp is not set up, there is no internet, or it fails, the same
 * message goes by SMS from the phone's SIM, so an alert is never lost just because WhatsApp
 * or the internet is down.
 */
final class Messenger {

    static final String SETUP_GUIDE = "https://www.callmebot.com/blog/free-api-whatsapp-messages/";

    private static final int FAIL = 0, WHATSAPP = 1, SMS = 2;

    private Messenger() {}

    /**
     * Sends {@code msg} to all loved ones on a background thread and runs {@code done}
     * exactly once when finished. Returns how many people it is trying to reach.
     */
    static int sendAll(Context ctx, String msg, Runnable done) {
        final Context c = ctx.getApplicationContext();
        final Runnable fin = done != null ? done : () -> { };
        final JSONArray a = Pulse.contacts(c);
        if (a.length() == 0) {
            Pulse.addLog(c, c.getString(R.string.log_sms_none));
            fin.run();
            return 0;
        }
        new Thread(() -> {
            ExecutorService ex = Executors.newFixedThreadPool(Math.min(a.length(), 4));
            try {
                List<Future<Integer>> results = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) {
                    final JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    names.add(o.optString("name", o.optString("phone")));
                    results.add(ex.submit(() -> deliver(c, o, msg)));
                }
                int wa = 0, sms = 0;
                List<String> failed = new ArrayList<>();
                for (int i = 0; i < results.size(); i++) {
                    int r = FAIL;
                    try {
                        r = results.get(i).get(45, TimeUnit.SECONDS);
                    } catch (Exception ignored) { }
                    if (r == WHATSAPP) wa++;
                    else if (r == SMS) sms++;
                    else failed.add(names.get(i));
                }
                Pulse.addLog(c, c.getString(R.string.log_sent, wa, sms));
                for (String n : failed) Pulse.addLog(c, c.getString(R.string.log_failed, n));
            } finally {
                ex.shutdownNow();
                fin.run();
            }
        }, "jp-send").start();
        return a.length();
    }

    private static int deliver(Context c, JSONObject o, String msg) {
        String phone = o.optString("phone", "");
        if (phone.isEmpty()) return FAIL;
        String key = o.optString("wa_key", "");
        if (!key.isEmpty() && online(c) && whatsapp(phone, key, msg)) return WHATSAPP;
        return sms(c, phone, msg) ? SMS : FAIL;
    }

    /** Sends one WhatsApp message through CallMeBot. Blocking: call off the main thread. */
    static boolean whatsapp(String phone, String key, String msg) {
        HttpURLConnection con = null;
        try {
            String url = "https://api.callmebot.com/whatsapp.php"
                    + "?phone=" + URLEncoder.encode(intl(phone), "UTF-8")
                    + "&text=" + URLEncoder.encode(msg, "UTF-8")
                    + "&apikey=" + URLEncoder.encode(key.trim(), "UTF-8");
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(15_000);
            con.setRequestProperty("User-Agent", "JeevanPulse");
            int code = con.getResponseCode();
            String body = "";
            InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n = in.read(buf);
                if (n > 0) body = new String(buf, 0, n, StandardCharsets.UTF_8);
                in.close();
            }
            String b = body.toLowerCase(Locale.ROOT);
            return code == 200 && !b.contains("error") && !b.contains("invalid");
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static boolean sms(Context c, String phone, String msg) {
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31
                    ? c.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            if (sm == null) return false;
            sm.sendMultipartTextMessage(phone, null, sm.divideMessage(msg), null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean online(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            if (cm == null) return false;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true; // can't tell: just try WhatsApp, SMS is the fallback anyway
        }
    }

    /** International format with +, assuming India (+91) for local numbers. */
    static String intl(String phone) {
        String d = phone.replaceAll("[^0-9]", "");
        if (phone.trim().startsWith("+")) return "+" + d;
        if (d.startsWith("00")) return "+" + d.substring(2);
        if (d.length() == 10) return "+91" + d;
        if (d.length() == 11 && d.startsWith("0")) return "+91" + d.substring(1);
        return "+" + d;
    }
}
