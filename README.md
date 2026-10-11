# Work Done

A personal Android app for logging workouts and following progress over time. Everything is
stored on the phone; there is no account or server.

## Features

- **Workout logging**: pick exercises (most used first) and log sets. Each set is pre-filled
  with the last values used, and a rest timer starts after each set. Past workouts can be logged
  by changing the start time.
- **Exercise types**: weight × reps, reps only, duration, distance + time, and three rowing
  machine types: intervals by time, intervals by distance, and a single distance in splits. The
  rowing types record the 500 m split, stroke rate, rest and resistance.
- **Rowing machine photos**: photograph a Concept2 PM5 "View Detail" screen and the intervals or
  splits are read on the phone (ML Kit text recognition), checked against the screen's totals,
  and shown for review before they're added.
- **History**: workouts grouped by week. Sets, times and notes can be edited afterwards.
- **Progress per exercise**: charts of estimated 1RM, max weight, volume, split, stroke rate and
  more, depending on the exercise type, plus personal records.
- **Statistics**: week streak, workouts and volume per week, and a calendar heatmap.
- **Backup**: export the database to a file and import it again, from Settings.

## Building

Requirements: Android Studio (it includes the JDK and Android SDK). From a terminal, point Gradle
at Android Studio's JDK:

```
export JAVA_HOME=/snap/android-studio/current/jbr
```

| Command | What it does |
|---|---|
| `./gradlew assembleDebug` | Builds the debug app |
| `./gradlew testDebugUnitTest` | Runs the unit tests (including Room and Robolectric tests) |
| `./gradlew assembleRelease` | Builds the release app (shrunk with R8, 64-bit ARM only) |

The debug and release builds are separate apps on the phone, with separate data:

| | Debug | Release |
|---|---|---|
| App ID | `com.mpernow.workdone.debug` | `com.mpernow.workdone` |
| Name and icon | "Work Done debug", red | "Work Done", green |
| Processor types | All (runs on the emulator) | `arm64-v8a` only |
| Use | Development and testing | Everyday use |

To build a release for the emulator, add `-Pworkdone.abis=x86_64`.

## Release signing

Release builds are signed with a personal key. The signing details live in `keystore.properties`
in the project root, which is ignored by git:

1. Create a key, keeping it outside the repository:

   ```
   $JAVA_HOME/bin/keytool -genkeypair -v -keystore /path/to/workdone-release.jks \
     -alias workdone -keyalg RSA -keysize 4096 -validity 36500
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in the path and passwords.
3. Back up the key and its password. Without them the installed app can't be updated, only
   reinstalled, which deletes its data.

Without `keystore.properties`, release builds are left unsigned and can't be installed.

## Deploying to a phone

Enable USB debugging on the phone (Settings → About phone → tap Build number 7 times, then
Developer options → USB debugging) and connect it. Then:

```
./gradlew installRelease   # the everyday app; updates in place and keeps its data
./gradlew installDebug     # a separate development copy
```

Pressing Run in Android Studio installs and starts the debug build.

Updates keep the app's data, and database changes are migrated automatically. To move data
between the debug and release apps, or to a new phone, use Settings → Export data in one and
Import data in the other. Settings such as kg/lb and the rest timer aren't part of the backup.

## Project layout

```
app/src/main/java/com/mpernow/workdone/
  domain/   Plain Kotlin logic: set input, progress and statistics, PM5 screen parsing
  data/     Room database, repositories, settings, backup, photo text recognition
  di/       AppContainer, which wires everything together by hand
  ui/       Jetpack Compose screens, one package per screen
app/schemas/  Exported database schemas, used by the migration tests
```
