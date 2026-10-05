# DoseDiary 💊

An offline-first, privacy-focused Android application designed for comprehensive medication management and symptom tracking. DoseDiary goes beyond basic reminders by integrating with official FDA data to proactively warn users about potential drug interactions, all while keeping sensitive health data strictly on the device.

## 🚀 Core Features

*   **Intelligent Medication Management:** Add medications via OpenFDA search or manual entry. Supports complex intake schedules (daily, every *N* days) and multiple doses per day.
*   **FDA Drug Interaction Checker:** Integrates with the `api.fda.gov` API via a Ktor client to analyze saved medications against official FDA labels. A purely unit-tested `InteractionMatcher` parses complex active substances, ignores noise, and rates interactions (High/Moderate severity).
*   **Robust Multi-Alarm System:** Guarantees timely reminders using `AlarmManager.setExactAndAllowWhileIdle` (firing reliably even in Doze mode). Gracefully falls back to WorkManager jobs if exact-alarm permissions are revoked.
*   **Symptom Diary & Linking:** Track symptoms with free-text entries and quick-tap tags (e.g., nausea, fatigue). Symptoms can be explicitly linked to specific medications to help identify side effects over time.
*   **Uncompromising Security & Privacy:** 
    *   Offline-first architecture ensures health data never leaves the device unless actively querying the FDA API.
    *   Biometric App Lock (`BiometricPrompt`) secures entry.
    *   `FLAG_SECURE` integration dynamically blocks screenshots, screen recording, and hides content in the recent apps overview.
*   **Polished UX & Performance:** Material 3 Light/Dark themes, pre-formatted text layers in ViewModels, immutable models mapped for Compose stability (`compose_stability.conf`), and a Baseline Profile for optimized release performance. 
*   **Seamless Localization:** Full English and Polish support with per-app language switching (compatible with pre-Android 13 devices).

## 🛠 Tech Stack

| Category | Technology |
| :--- | :--- |
| **Core** | Kotlin 2.0.21, JVM 17, Min SDK 26, Target SDK 35 |
| **UI** | Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose |
| **Architecture** | Clean Architecture, MVVM / MVI (Unidirectional Data Flow) |
| **Dependency Injection** | Koin 4.0.0 (Core, Android, Compose) |
| **Concurrency** | Kotlin Coroutines 1.9.0, StateFlow / Flow |
| **Local Storage** | Room 2.7.1 (Schema v5), DataStore Preferences |
| **Networking** | Ktor 3.0.2 (OkHttp engine), `kotlinx.serialization` |
| **Background Processing** | WorkManager 2.10.0, AlarmManager |
| **Security** | AndroidX Biometric 1.1.0, Window `FLAG_SECURE` |
| **Build System** | Gradle Version Catalogs (`libs.versions.toml`), KSP, AGP 8.4.0 |

## 🏛 Architecture

The application strictly adheres to **Clean Architecture** principles, enforcing separation of concerns across three distinct layers. Dependencies point strictly inward, ensuring the domain layer remains entirely agnostic of the Android framework.

1.  **Domain Layer (Pure Kotlin):** Contains core models (`Medication`, `Symptom`), Use Cases, repository interfaces, and pure business logic (`IntakeSchedule`, `InteractionMatcher`). Utilizes an injectable `Clock` for deterministic time-based testing.
2.  **Data Layer:** Handles external data sources. Includes Room DAOs and schema migrations (v1 to v5), Ktor OpenFDA API client, DataStore preferences, WorkManager schedulers, and interface implementations.
3.  **Presentation Layer:** Houses Jetpack Compose screens and ViewModels. ViewModels expose state via `StateFlow`, adhering to strict MVVM/MVI unidirectional data flow patterns.
4.  **Analytics Abstraction:** Implements a domain-level `AnalyticsLogger` with a Logcat debug implementation, preparing the app for seamless production tracking (e.g., Firebase/Amplitude) without polluting UI code.

## 🧪 Testing

The project emphasizes reliability through both unit and instrumented testing:
*   **Unit Tests:** Extensive coverage of pure logic, including the `InteractionMatcher`, intake/reminder scheduling, Koin dependency graphs, ViewModels, and OpenFDA repositories (using Ktor's MockEngine). 
*   **Instrumented Tests:** Verification of Compose UI components (e.g., Symptom Tag Rows) and strict validation of Room database migrations against exported schemas using `MigrationTestHelper`.

## 🚀 Getting Started

### Prerequisites
*   Android Studio (compatible with AGP 8.4.0)
*   JDK 17

### Build & Run
To run the debug version of the app locally:

```bash
# Install debug APK on a connected device/emulator
./gradlew installDebug

# Run JVM Unit Tests
./gradlew testDebugUnitTest

# Run Instrumented Tests (Requires connected device/emulator)
./gradlew connectedDebugAndroidTest
``'

### Release Build Setup
To ensure repository security, release signing credentials are not tracked in Git. To build a signed release APK (which utilizes R8 minification and resource shrinking), you must configure a `local.properties` file in the project root:

```properties
signing.storeFile=C:/path/to/your-keystore.jks
signing.storePassword=your_store_password
signing.keyAlias=your_key_alias
signing.keyPassword=your_key_password
```

Execute `./gradlew assembleRelease` to build. If these properties or the keystore file are missing, the build process will safely fallback to producing an unsigned release APK.
