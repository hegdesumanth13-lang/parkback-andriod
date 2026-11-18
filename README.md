# ParkBack Pro

A native Android parking assistant. Save a fresh device location, walk away, and use the dashboard to find your way back. The latest save is the active session. Replacing it records an end time and keeps its notes and photo in History.

## Open on Windows

1. Extract the entire ZIP. Open Android Studio, choose **File → Open**, and select **ParkBackPro**, the folder containing `settings.gradle.kts`.
2. Use Android Studio Ladybug 2024.2.1 or newer with the Gradle JDK set to **17**. In SDK Manager install Android SDK Platform **35**, Build Tools **34.0.0**, and Platform Tools. Accept the SDK licenses.
3. Sync the project. The first sync needs access to Google's Android Maven repository, Maven Central, the Gradle Plugin Portal and Gradle's distribution servers. Use the included Gradle wrapper.
4. Connect an Android 8.0+ phone with Google Play services via USB. Enable USB debugging, unlock the phone and accept the computer's debugging prompt.
5. Select **app** and the phone in Android Studio and click **Run**. Outdoors, tap **Save My Parking Spot** and grant location access. Precise location makes the demo easier to interpret.

From PowerShell, inside the extracted project:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat installDebug
.\gradlew.bat connectedDebugAndroidTest
```

A successful debug build writes `app/build/outputs/apk/debug/app-debug.apk`. Uninstalling or clearing app data erases saved sessions and photos.

## Screens

| Screen | What you can do |
|---|---|
| Home | Save a fresh position, confirm replacement, see elapsed time, measured accuracy and live straight-line distance. Compare the two markers on the interactive map or launch Google Maps walking directions. |
| Parking details | View coordinates, floor, pillar, zone, notes and a private photo. Take or select a photo, edit or cancel a reminder, share the location and notes, copy coordinates, or delete the record. |
| History | Browse actual saves by local date, view completed durations, search notes or filter with the date picker. Open any record on its map. Clearing completed history preserves the active record. |
| Photo viewer | Open a saved image at screen size and return to the same record. |
| Settings | Choose light, dark or system appearance. The preference survives restarts. |

## Implementation

Kotlin 2.0.21, Compose Material 3, Navigation Compose, coroutines and lifecycle-aware StateFlow. The AndroidViewModel coordinates a transactional Room DAO, a fused-location repository, private files, DataStore and WorkManager. Min SDK 26, compile and target SDK 35, AGP 8.7.3, Gradle 8.9 and Java 17.

The save workflow requests a new location with zero allowed cache age. A failed request preserves the previous session. The unique active index and Room transaction prevent two simultaneous active sessions. Completed records have a null active key. Live updates run while the dashboard is resumed. The distance uses Android Location.distanceTo. The compass uses a rotation vector and local magnetic declination.

The MapLibre map loads the OpenFreeMap Liberty or Dark style. It draws a car marker, a live position marker and a direct line between them. Recenter restores the view after manual panning. External directions use an HTTPS walking-directions URL and fall back to the default browser or URL handler. Map data comes from OpenStreetMap contributors via OpenMapTiles and OpenFreeMap.

Provider information: https://openfreemap.org/ and https://www.openstreetmap.org/copyright

## Permissions and device behavior

- Foreground coarse and fine location are requested together when saving a spot. Approximate-only access works with a wider uncertainty radius. Android settings allow changing a previous denial.
- The camera app receives a temporary file URI. The photo picker grants access to a selected image. Saved copies live in private app storage.
- On Android 13+, enabling a reminder requests notification permission. Android settings control the reminder channel. WorkManager is deferrable, and power saving can delay delivery. Replace or delete a spot to cancel its reminder.
- Internet access loads map tiles. Stored details, photos, notes and history are local. Obtaining a fresh fix requires working device location services, even when offline.
- GPS and a magnetic compass can perform poorly near steel or underground. Accuracy labels use reported uncertainty (good up to 15 m, moderate up to 50 m). Below the uncertainty range, look for your saved floor, pillar or photo.

## Physical-phone checklist

| Check | Expected result |
|---|---|
| Fresh install | Empty dashboard and history. No sample records. |
| Deny location, including permanent denial | Save fails clearly. App settings allow recovery. |
| Grant approximate only | Real coordinates save when available. The dashboard explains reduced precision. |
| Turn off device location or attempt an indoor save | A failure or timeout preserves an existing record. A successful but poor fix shows its uncertainty. |
| Save outdoors, walk 50–100 m, then walk back | Distance and blue marker change as fixes arrive. Car stays fixed. The line is straight-line distance. |
| Pan, zoom, recenter and tap the car marker | Recenter frames available points. The car opens details. |
| Background and reopen, rotate and reboot | Saved time, coordinates, history and theme persist. Live position resumes on the dashboard. |
| Replace a spot, cancel replacement, edit the old record | Confirmed saves create separate sessions. Cancellation and historical edits preserve the active spot. |
| Capture a photo, choose another, cancel either flow | Correct record shows the saved photo. Cancellation preserves the previous image. |
| Open walking directions with and without Google Maps | Actual parking coordinates are the destination. Browser fallback works when a handler is installed. |
| Go offline, then restore connectivity | Saved details remain available. Retry the map after restoring service. |
| Schedule, change and cancel a reminder. Repeat with notifications denied | Allowed reminder opens ParkBack when delivered. Denial has a settings recovery path. |
| Search by note or date, delete one entry, clear completed history | Matching records and confirmed deletions are correct. Current spot survives clearing completed history. |
| Switch light/dark, rotate phone and try compass near a metal object | The UI remains readable. The compass is approximate and handles missing sensors. |
| Share, copy coordinates and open full-size photo | The selected record supplies the link, clipboard coordinates, notes and photo. |

## Limitations and possible improvements

A saved spot tracks the phone's location at the moment of capture. There is no indoor positioning, guaranteed offline map region or exact-time alarm. Map service availability, Google Play services, notification settings and the phone's hardware affect the demonstration. Future improvements could include an explicit end-session action and an opt-in local history export.

See **VALIDATION.md** for the results from this delivery environment.
