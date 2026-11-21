# JeevanPulse ♥

**One tap tells your loved ones you're OK.** A simple Android safety check-in app, in English, हिंदी and मराठी (it follows the phone's language).

## How it works
1. JeevanPulse sends a notification: **"Are you OK?"** with an **I'm OK** button.
2. **Tapped:** it's logged on the phone and nothing is sent.
3. **Not tapped:** every loved one gets an **SMS** alert.
4. **3 missed in a row:** the **emergency contact is phoned** automatically.
5. **Tapped any time later:** the counter resets, and loved ones get an "all OK" SMS.

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

## Get the app
GitHub Actions builds the APK on every push to `main` (about 3–5 minutes).
On the phone, open **Releases → JeevanPulse (latest build) → JeevanPulse.apk**, download it, and install it. Allow "Install unknown apps" when asked.

### First run
1. Add at least one loved one (**+ Add from contacts**).
2. Tap **Start JeevanPulse**, and **allow** Notifications, SMS and Phone.
3. When asked, allow **"Alarms & reminders"** and **"Don't optimise battery"**, so reminders come on time.
   On Xiaomi, Oppo, Vivo and Realme phones, also turn on **Autostart** for JeevanPulse in the phone settings.

## Notes
- SMS and calls use the phone's own SIM, so normal SMS and call charges apply.
- The automatic SMS and call permissions are fine for a sideloaded APK. Google Play restricts them, so a Play Store version would need a server to send SMS and calls instead (e.g. MSG91 / Exotel).
- The app works offline. Nothing leaves the phone except the SMS and calls you configure.

## Project layout
```
app/src/main/java/com/jeevanpulse/app/
  MainActivity.java    the screen (status, I'm OK, loved ones, settings, log)
  Pulse.java           all logic: schedule, missed counter, SMS, call
  AlarmReceiver.java   fires each check-in
  OkReceiver.java      the "I'm OK" button on the notification
  BootReceiver.java    restores the schedule after reboot
app/src/main/res/      layout, strings (en / hi / mr), icons
.github/workflows/android.yml   builds and publishes the APK
```
