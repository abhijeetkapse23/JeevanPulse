package com.jeevanpulse.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 10;
    private static final int REQ_PICK = 20;

    private TextView tvStatus, tvNext, tvContactHint, tvEmpty;
    private Button btnOk, btnStart, btnAdd;
    private Spinner spMode;
    private EditText etName;
    private LinearLayout llContacts, llLog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean spinnerReady = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Pulse.channels(this);

        tvStatus = findViewById(R.id.tvStatus);
        tvNext = findViewById(R.id.tvNext);
        tvContactHint = findViewById(R.id.tvContactHint);
        tvEmpty = findViewById(R.id.tvLogEmpty);
        btnOk = findViewById(R.id.btnOk);
        btnStart = findViewById(R.id.btnStart);
        btnAdd = findViewById(R.id.btnAdd);
        spMode = findViewById(R.id.spMode);
        etName = findViewById(R.id.etName);
        llContacts = findViewById(R.id.llContacts);
        llLog = findViewById(R.id.llLog);

        btnOk.setOnClickListener(v -> {
            Pulse.ok(this, null);
            Toast.makeText(this, R.string.toast_ok, Toast.LENGTH_SHORT).show();
            refresh();
        });

        btnStart.setOnClickListener(v -> {
            if (Pulse.active(this)) {
                Pulse.stop(this);
            } else {
                askPermissions();
                Pulse.start(this);
            }
            refresh();
        });

        btnAdd.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try {
                startActivityForResult(pick, REQ_PICK);
            } catch (Exception e) {
                Toast.makeText(this, "No contacts app found", Toast.LENGTH_SHORT).show();
            }
        });

        ArrayAdapter<CharSequence> ad = ArrayAdapter.createFromResource(this, R.array.modes,
                android.R.layout.simple_spinner_item);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMode.setAdapter(ad);
        spMode.setSelection(Pulse.mode(this));
        spMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) { spinnerReady = true; return; }
                if (pos == Pulse.mode(MainActivity.this)) return;
                Pulse.sp(MainActivity.this).edit().putInt("mode", pos).apply();
                if (Pulse.active(MainActivity.this)) Pulse.start(MainActivity.this);
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        etName.setText(Pulse.name(this));
        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                Pulse.sp(MainActivity.this).edit().putString("name", s.toString()).apply();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    // ---------- permissions ----------

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && !Pulse.granted(this, Manifest.permission.POST_NOTIFICATIONS))
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!Pulse.granted(this, Manifest.permission.SEND_SMS)) need.add(Manifest.permission.SEND_SMS);
        if (!Pulse.granted(this, Manifest.permission.CALL_PHONE)) need.add(Manifest.permission.CALL_PHONE);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        } else {
            askSystemSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERMS) return;
        for (int r : results) {
            if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.toast_need_perm, Toast.LENGTH_LONG).show();
                break;
            }
        }
        askSystemSettings();
    }

    /** Exact alarms and battery optimisation, so reminders come on time. */
    private void askSystemSettings() {
        try {
            AlarmManager am = getSystemService(AlarmManager.class);
            if (Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception ignored) { }
    }

    // ---------- contacts ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        String[] cols = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor cur = getContentResolver().query(data.getData(), cols, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String name = cur.getString(0);
                String phone = cur.getString(1);
                if (phone == null) return;
                phone = phone.replaceAll("[^0-9+]", "");
                JSONArray a = Pulse.contacts(this);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && phone.equals(o.optString("phone"))) return; // already added
                }
                JSONObject o = new JSONObject();
                o.put("name", name == null ? phone : name);
                o.put("phone", phone);
                // The first person added becomes the emergency contact automatically.
                o.put("emergency", Pulse.emergencyContact(this) == null);
                a.put(o);
                Pulse.saveContacts(this, a);
                Pulse.addLog(this, getString(R.string.log_contact_added, o.optString("name")));
            }
        } catch (JSONException | SecurityException e) {
            Toast.makeText(this, "Could not read contact", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void setEmergency(int index) {
        JSONArray a = Pulse.contacts(this);
        try {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                o.put("emergency", i == index);
            }
        } catch (JSONException ignored) { }
        Pulse.saveContacts(this, a);
        refresh();
    }

    // ---------- WhatsApp ----------

    /** One-time WhatsApp setup for a loved one: they activate CallMeBot and give us their key. */
    private void whatsappDialog(int index) {
        JSONArray a = Pulse.contacts(this);
        JSONObject o = a.optJSONObject(index);
        if (o == null) return;
        final String name = o.optString("name");
        final String phone = o.optString("phone");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);

        TextView info = new TextView(this);
        info.setText(getString(R.string.wa_dialog_text, name));
        info.setTextSize(14);
        info.setTextColor(0xFF4A3F35);
        info.setLineSpacing(dp(3), 1f);
        info.setTextIsSelectable(true);
        box.addView(info);

        final EditText key = new EditText(this);
        key.setHint(R.string.wa_key_hint);
        key.setInputType(InputType.TYPE_CLASS_NUMBER);
        key.setText(o.optString("wa_key", ""));
        box.addView(key);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.wa_dialog_title, name))
                .setView(box)
                .setPositiveButton(R.string.wa_save, (dlg, w) -> saveWaKey(index, key.getText().toString()))
                .setNegativeButton(R.string.wa_guide, null)
                .setNeutralButton(R.string.wa_test, null)
                .create();
        d.setOnShowListener(x -> {
            // These two must not close the dialog.
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Messenger.SETUP_GUIDE)));
                } catch (Exception ignored) { }
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                final String k = key.getText().toString().trim();
                if (k.isEmpty()) {
                    Toast.makeText(this, R.string.wa_key_hint, Toast.LENGTH_SHORT).show();
                    return;
                }
                saveWaKey(index, k);
                Toast.makeText(this, R.string.wa_testing, Toast.LENGTH_SHORT).show();
                final String msg = getString(R.string.wa_test_msg, Pulse.displayName(this));
                new Thread(() -> {
                    boolean okSent = Messenger.whatsapp(phone, k, msg);
                    runOnUiThread(() -> {
                        Toast.makeText(this, okSent ? R.string.wa_test_ok : R.string.wa_test_fail,
                                Toast.LENGTH_LONG).show();
                        Pulse.addLog(this, getString(okSent ? R.string.log_wa_test_ok
                                : R.string.log_wa_test_fail, name));
                        refresh();
                    });
                }).start();
            });
        });
        d.show();
    }

    private void saveWaKey(int index, String key) {
        JSONArray a = Pulse.contacts(this);
        JSONObject o = a.optJSONObject(index);
        if (o == null) return;
        try {
            String k = key.trim();
            if (k.isEmpty()) o.remove("wa_key");
            else o.put("wa_key", k);
        } catch (JSONException ignored) { }
        Pulse.saveContacts(this, a);
        refresh();
    }

    private void removeContact(int index) {
        JSONArray a = Pulse.contacts(this);
        JSONArray n = new JSONArray();
        boolean removedEmergency = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (i == index) { removedEmergency = o != null && o.optBoolean("emergency"); continue; }
            n.put(o);
        }
        // Keep someone as emergency contact if possible.
        if (removedEmergency && n.length() > 0) {
            try { n.getJSONObject(0).put("emergency", true); } catch (JSONException ignored) { }
        }
        Pulse.saveContacts(this, n);
        refresh();
    }

    // ---------- UI ----------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        boolean active = Pulse.active(this);
        int missed = Pulse.missed(this);
        long next = Pulse.nextAt(this);

        if (!active) {
            tvStatus.setText(R.string.status_off);
            tvStatus.setTextColor(0xFF7A6A5B);
            tvNext.setText(R.string.status_off_hint);
            btnStart.setText(R.string.start);
            btnStart.setBackgroundResource(R.drawable.bg_btn);
        } else {
            btnStart.setText(R.string.stop);
            btnStart.setBackgroundResource(R.drawable.bg_btn_dark);
            if (Pulse.awaiting(this)) {
                if (missed == 0) {
                    tvStatus.setText(R.string.status_waiting);
                    tvStatus.setTextColor(0xFFB07800);
                } else {
                    tvStatus.setText(getString(R.string.status_missed, missed, Pulse.CALL_AFTER));
                    tvStatus.setTextColor(0xFFD64545);
                }
                tvNext.setText(getString(R.string.next_reminder, Pulse.time(this, next)));
            } else {
                tvStatus.setText(R.string.status_good);
                tvStatus.setTextColor(0xFF1F9D6B);
                tvNext.setText(getString(R.string.next_check, Pulse.time(this, next)));
            }
        }

        // contacts
        llContacts.removeAllViews();
        JSONArray a = Pulse.contacts(this);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final int idx = i;
            boolean em = o.optBoolean("emergency");

            boolean wa = !o.optString("wa_key", "").isEmpty();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(10), 0, dp(10));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = new TextView(this);
            t.setText(o.optString("name") + "\n" + o.optString("phone"));
            t.setTextSize(15);
            t.setTextColor(0xFF2B2118);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button bX = new Button(this);
            bX.setText("✕");
            bX.setTextColor(0xFFB9A898);
            bX.setBackgroundColor(Color.TRANSPARENT);
            bX.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.remove_confirm, o.optString("name")))
                    .setPositiveButton(R.string.remove, (dlg, w) -> removeContact(idx))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            row.addView(bX, new LinearLayout.LayoutParams(dp(44), dp(40)));
            card.addView(row);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(6), 0, 0);

            Button bE = new Button(this);
            bE.setAllCaps(false);
            bE.setTextSize(12);
            bE.setText(em ? R.string.emergency : R.string.make_emergency);
            bE.setTextColor(em ? Color.WHITE : 0xFF7A6A5B);
            bE.setBackgroundResource(em ? R.drawable.bg_chip_red : R.drawable.bg_chip);
            bE.setPadding(dp(10), 0, dp(10), 0);
            bE.setOnClickListener(v -> setEmergency(idx));
            LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            lpE.setMarginEnd(dp(8));
            chips.addView(bE, lpE);

            Button bW = new Button(this);
            bW.setAllCaps(false);
            bW.setTextSize(12);
            bW.setText(wa ? R.string.whatsapp_on : R.string.whatsapp_setup);
            bW.setTextColor(wa ? Color.WHITE : 0xFF1F7A4D);
            bW.setBackgroundResource(wa ? R.drawable.bg_chip_green : R.drawable.bg_chip);
            bW.setPadding(dp(10), 0, dp(10), 0);
            bW.setOnClickListener(v -> whatsappDialog(idx));
            chips.addView(bW, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));

            card.addView(chips);
            llContacts.addView(card);
        }
        if (a.length() == 0) {
            tvContactHint.setVisibility(View.VISIBLE);
            tvContactHint.setText(R.string.no_contacts);
        } else if (Pulse.emergencyContact(this) == null) {
            tvContactHint.setVisibility(View.VISIBLE);
            tvContactHint.setText(R.string.no_emergency);
        } else {
            tvContactHint.setVisibility(View.GONE);
        }

        // log
        llLog.removeAllViews();
        JSONArray lg = Pulse.log(this);
        for (int i = 0; i < lg.length() && i < 25; i++) {
            JSONObject o = lg.optJSONObject(i);
            if (o == null) continue;
            TextView t = new TextView(this);
            t.setText(Pulse.time(this, o.optLong("t")) + "  ·  " + o.optString("x"));
            t.setTextSize(13);
            t.setTextColor(0xFF4A3F35);
            t.setPadding(0, dp(5), 0, dp(5));
            llLog.addView(t);
        }
        tvEmpty.setVisibility(lg.length() == 0 ? View.VISIBLE : View.GONE);
    }
}
