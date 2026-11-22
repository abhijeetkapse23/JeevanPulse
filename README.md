# JeevanPulse ♥

**One tap tells your loved ones you're OK.** A simple Android safety check-in app, in English, हिंदी and मराठी (it follows the phone's language).

## How it works
1. JeevanPulse sends a notification: **"Are you OK?"** with an **I'm OK** button.
2. **Tapped:** it's logged on the phone and nothing is sent.
3. **Not tapped:** every loved one gets a **WhatsApp** alert. If WhatsApp isn't set up for that person, there's no internet, or WhatsApp fails, they get an **SMS** instead, so an alert is never lost.
4. **3 missed in a row:** the **emergency contact is phoned** automatically.
5. **Tapped any time later:** the counter resets, and loved ones get an "all OK" message.
6. **Battery at 15%:** loved ones are told once, because a phone that dies can't send alerts.

- Loved ones are picked from the **phone's contact list**, with no typing of numbers.
- The first person added becomes the **emergency contact**. Tap "Make emergency" to change it.
- **112 is never dialled automatically.** If there is no emergency contact, the app only shows a notification that opens the dialer with 112.

### Check-in interval
| Setting | Asks every | Time to answer before it counts as missed |
|---|---|---|
| 2 minutes (testing) | 2 min | 2 min |
| 12 hours | 12 h | 30 min |
| Daily | 24 h | 30 min |

With **testing** mode: the first reminder comes 2 min after Start. No tap leads to an SMS at 4 min, and still no tap leads to a call at 8 min.

## WhatsApp alerts (free)
WhatsApp messages are sent through the free [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/) API (free for personal use). Each loved one does this **once, on their own phone**:
1. Save the CallMeBot number shown on the CallMeBot setup page.
2. On WhatsApp, send it: `I allow callmebot to send me messages`
3. CallMeBot replies with an **API key**.
4. In JeevanPulse, tap **Set up WhatsApp** under that person, enter the key, and tap **Send test**.

People without a key still get SMS. CallMeBot is a free third-party service with no uptime guarantee, which is why SMS stays as the automatic fallback.

## Privacy and security
- Loved ones and their WhatsApp keys are stored **only on the phone**, encrypted with AES-256 using a key held in the Android Keystore. Nothing is in this repository or on any server.
- App backups are turned off, so the list isn't copied to cloud backups.
- Alert text goes to CallMeBot (for WhatsApp) or the mobile network (for SMS) only when an alert is sent.

## Signing key
Older versions signed the APK with a keystore and password committed to this repo. Because the repo is public, anyone could sign a fake "update". To switch to a private key:
1. Create one: `keytool -genkeypair -v -keystore release.keystore -alias jeevanpulse -keyalg RSA -keysize 2048 -validity 10000`
2. In GitHub: **Settings → Secrets and variables → Actions**, add `JP_KEYSTORE_BASE64` (output of `base64 -w0 release.keystore`), `JP_STORE_PASSWORD`, `JP_KEY_ALIAS` (`jeevanpulse`) and `JP_KEY_PASSWORD`.
3. Uninstall the old app once and install the new build (a new key can't update an app signed with the old one).
4. Delete `app/jeevanpulse.keystore` from the repo, or make the repo private.

Until the secrets are set, builds keep using the old key.

## Get the app
GitHub Actions builds the APK on every push to `main` (about 3–5 minutes).
On the phone, open **Releases → JeevanPulse (latest build) → JeevanPulse.apk**, download it, and install it. Allow "Install unknown apps" when asked.

### First run
1. Add at least one loved one (**+ Add from contacts**).
2. Set up WhatsApp for each person (optional, see above).
3. Tap **Start JeevanPulse**, and **allow** Notifications, SMS and Phone.
4. When asked, allow **"Alarms & reminders"** and **"Don't optimise battery"**, so reminders come on time.
   On Xiaomi, Oppo, Vivo and Realme phones, also turn on **Autostart** for JeevanPulse in the phone settings.

## Notes
- SMS and calls use the phone's own SIM, so normal SMS and call charges apply. WhatsApp alerts need mobile data or Wi-Fi.
- The automatic SMS and call permissions are fine for a sideloaded APK. Google Play restricts them, so a Play Store version would need a server to send SMS and calls instead (e.g. MSG91 / Exotel).
- Without internet, alerts still go out by SMS.

## Project layout
```
app/src/main/java/com/jeevanpulse/app/
  MainActivity.java    the screen (status, I'm OK, loved ones, settings, log)
  Pulse.java           all logic: schedule, missed counter, battery check, call
  Messenger.java       sends alerts: WhatsApp (CallMeBot) with SMS fallback
  Vault.java           encrypts stored contacts with the Android Keystore
  BatteryReceiver.java periodic low-battery check
  AlarmReceiver.java   fires each check-in
  OkReceiver.java      the "I'm OK" button on the notification
  BootReceiver.java    restores the schedule after reboot
app/src/main/res/      layout, strings (en / hi / mr), icons
.github/workflows/android.yml   builds and publishes the APK
```
