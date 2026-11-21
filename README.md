# JeevanPulse ♥

**One daily pulse of well-being.** A check-in and medicine-care app for families in India, in English, हिंदी and मराठी.

- **Daily JeevanPulse:** one tap tells family "I'm OK today".
- **Medicine + JeevanPulse mode:** ticking your medicines counts as the day's pulse, so there's no double-tapping.
- **Quick view** ("Which all today?") and **Deep view** (time, days, before/after food, dose, refill reminders).
- **History:** a 14-day calendar plus pattern spotting, e.g. "Night dose skipped 3 of last 7 days".
- **Family view:** calm status cards (Pulse active, All meds taken, Dose pending, Skipped/Overdue).
- **Alerts in two stages:**
  1. When the pulse or a dose is missed (after a grace window), family gets a ready-to-send WhatsApp message.
  2. If there's still no pulse a few hours later, the emergency contact is alerted, with Call and 112 buttons.
- **Phone reminders** (Android app) for each medicine and the daily pulse.

## Get the app
The Android APK is built automatically by GitHub Actions on every push to `main` (about 5–8 minutes).
Download it from **Releases → JeevanPulse (latest build) → JeevanPulse.apk**.

### Install on an Android phone
1. On the phone, open the repo's **Releases** page and download `JeevanPulse.apk`.
2. Open it. When asked, allow **"Install unknown apps"** for your browser.
3. Open JeevanPulse and allow notifications, so medicine and pulse reminders work.

## Project layout
```
www/                 the app (HTML/CSS/JS, one file) + icons
assets/icon.png      app icon used for the Android build
capacitor.config.json
.github/workflows/   android.yml (APK build)
```

## Roadmap: fully automatic alerts
Right now the alerts open WhatsApp with the message ready to send, and someone taps Send. For hands-free alerts, even when the phone is off:
- **Firebase** (Auth + Firestore + a scheduled Cloud Function that checks for missed pulses every few minutes)
- **WhatsApp Business API** (Gupshup / Interakt / Meta Cloud API) with approved message templates
- **SMS** via MSG91 (DLT registration required in India)
- **Automatic voice call** to the emergency contact via Exotel
- A **family login** to see status cards remotely
- Privacy policy and consent screens (DPDP Act 2023) before public launch
