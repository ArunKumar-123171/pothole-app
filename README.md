# RoadTwin AI — Road Damage Detection and Monitoring System Using AI

[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML Engine](https://img.shields.io/badge/Inference-LiteRT%20%2F%20TFLite%20Play%20Services-FF6F00?logo=tensorflow&logoColor=white)](https://developers.google.com/android/guides/setup)
[![Database](https://img.shields.io/badge/Database-Room%20SQLite%20(Offline--First)-4285F4)](https://developer.android.com/training/data-storage/room)
[![Build Status](https://img.shields.io/badge/Tests-58%20Passed-brightgreen)](https://github.com/ArunKumar-123171/pothole-app)

---

## 1. Project Title
**RoadTwin AI: Road Damage Detection and Monitoring System Using AI**  
An edge-first Android application designed for automated, real-time asphalt surface inspection, pothole detection, GPS path tracking, and municipal report generation.

---

## 2. Project Overview
RoadTwin AI transforms standard Android smartphones into intelligent road inspection devices. Mounted inside a vehicle or survey vehicle windshield, the device uses its camera and onboard hardware sensors to detect road surface defects (potholes) in real time during transit.

All computer vision inference is executed on-device using a single-class **YOLO26n FP32** model via Google Play Services LiteRT/TensorFlow Lite. The application logs high-precision GPS telemetry, records travel trajectories, tracks pothole occurrences across multiple frames, stores high-resolution crop evidence locally, computes dynamic road health ratings, renders professional municipal inspection PDF reports with vector route maps, and synchronizes structured telemetry with Firebase Firestore when network connectivity is available.

---

## 3. Problem Statement
Traditional road surface monitoring and municipal pavement management face critical bottlenecks:
- **Manual Visual Surveys**: Road inspection crews rely on manual visual observations, which are labor-intensive, slow, and hazardous.
- **Expensive Dedicated Survey Vehicles**: Specialized laser profilometer / LiDAR survey trucks cost hundreds of thousands of dollars, making regular municipal inspections unaffordable.
- **Reporting Inconsistencies**: Inspection logs often lack verifiable geolocation stamps, high-resolution visual evidence, or standardized damage severity classifications.
- **Delayed Maintenance Interventions**: Potholes deteriorate rapidly under rain and heavy traffic; delayed detection leads to vehicle damage, road accidents, and costly asphalt reconstruction.

---

## 4. Proposed Solution
RoadTwin AI addresses these challenges with an accessible, edge-computing mobile solution:
1. **Ubiquitous Hardware Deployment**: Turns widely available Android smartphones into autonomous inspection units.
2. **Real-Time Edge AI**: Executes low-latency YOLO26n object detection directly on the mobile device without relying on continuous cloud connectivity.
3. **Multi-Frame Tracking & Deduplication**: Employs spatial and temporal deduplication (3-meter radius and 3-second cooldown) and consecutive-frame verification (3+ stable frames) to prevent false positives and duplicate counts.
4. **Offline-First Resilience**: Stores all inspection data, route breadcrumbs, and image evidence in a local Room SQLite database.
5. **Instant Municipal PDF Generation**: Compiles an official 4-stage engineering report directly on the device with a canvas survey route polyline and localized repair recommendations.
6. **Low-Bandwidth Cloud Synchronization**: Synchronizes lightweight structured JSON metadata to Firebase Firestore while preserving heavy images on local device storage.

---

## 5. Key Features
- **Real-Time Pothole Detection**: Continuous video analysis via CameraX and YOLO26n single-class model at 10–15 FPS with ~12 ms latency.
- **Centroid & IoU Object Tracking**: Motion-aware tracking matching detections across frames and filtering transient visual artifacts.
- **Intelligent Duplicate Suppression**: Spatial suppression ($3.0\text{ m}$) and temporal cooldown ($3000\text{ ms}$) prevent re-reporting the same defect while allowing distinct potholes in quick succession.
- **High-Accuracy GPS Trajectory Recording**: Continuous location streaming using `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, capturing real start coordinates, intermediate waypoints, and end coordinates.
- **Dynamic Road Health Score**: Algorithmic scoring ($0 - 100$) derived from defect frequency and severity weighting (`Critical: -12`, `High: -8`, `Medium: -4`, `Low: -1`).
- **4-Stage Municipal PDF Generator**: Android `PdfDocument` and `Canvas` rendering engine producing official municipal reports including route polylines, start/end pins, pothole markers, and genuine camera snapshots.
- **Zero-Cloud Dependency for Core Operation**: Full monitoring, evidence logging, and PDF export work completely offline without internet connectivity.
- **Background Cloud Synchronization**: WorkManager orchestrates automatic one-time or periodic (hourly) synchronization of completed session metadata to Firestore.
- **Modern Jetpack Compose UI**: Lightweight, responsive user interface featuring a prominent one-tap "START MONITORING" hero card, detailed inspection history, interactive route map, and benchmark telemetry.

---

## 6. System Workflow

```
[ CameraX Video Stream ]                [ FusedLocationProviderClient ]
          │                                           │
          ▼                                           ▼
[ 416x416 Letterbox Preprocessing ]     [ High-Accuracy GPS Fix (±X.X m) ]
          │                                           │
          ▼                                           ▼
[ YOLO26n TFLite Inference (~12 ms) ]   [ Distance Accumulation (Haversine) ]
          │                                           │
          ▼                                           │
[ Bounding Boxes & Confidence Scores ]                │
          │                                           │
          ▼                                           │
[ Centroid & IoU Tracking (3+ Frames) ]               │
          │                                           │
          ▼                                           │
[ Spatial/Temporal Duplicate Filter ] ────────────────┘
          │
          ▼
[ Capture JPEG Evidence Snapshot ]
          │
          ▼
[ Room SQLite Database (monitoring_sessions, detections, route_points) ]
          │
          ├──────────────────────────────────────────┐
          ▼                                          ▼
[ On-Device PDF Generator ]             [ WorkManager SyncWorker ]
  • Executive Summary                     • Network Constraint
  • Vector Canvas Route Map               • JSON Metadata Payload
  • Photo Evidence Cards                  • Firestore `reports` Collection
  • Technical Audit & Specs
```

---

## 7. System Architecture
RoadTwin AI follows modern Android clean architecture principles and separation of concerns:

- **Presentation Layer (UI)**: Built with 100% Jetpack Compose using Material 3 design tokens. Unidirectional data flow (UDF) powered by Android Architecture Components `ViewModel` and Kotlin `StateFlow`.
- **Domain & ML Engine**: 
  - `TFLiteObjectDetector`: Manages Google Play Services LiteRT runtime, memory-mapped assets, letterboxing, and tensor buffer marshaling.
  - `DetectionTracker`: Manages active pothole tracks, frame stability counters, and Haversine spatial duplicate filtering.
- **Data Layer (Repository & Local Storage)**:
  - `ReportsRepository` / `DefaultReportsRepository`: Central data gateway coordinating Room DAOs, device file storage, and sync workers.
  - `RoadTwinDatabase`: Room SQLite database with entities for `MonitoringSessionEntity`, `DetectionEntity`, and `RoutePointEntity`.
  - `PdfReportGenerator`: Custom 2D vector graphics and page compositor built on native `android.graphics.pdf.PdfDocument`.
- **Remote Synchronization Layer**:
  - `FirebaseSyncManager`: Serializes completed session records and coordinates to Firestore documents.
  - `SyncWorker`: Scheduled background worker respecting Android battery optimization and network availability constraints.

---

## 8. Technology Stack

| Category | Component / Library | Details |
|---|---|---|
| **Language** | Kotlin 2.0+ | Coroutines, StateFlow, Serialization |
| **UI Framework** | Jetpack Compose (BOM) | Material 3, Navigation Compose, Custom Canvas |
| **Camera Framework** | AndroidX CameraX (1.4+) | Camera2 integration, ImageAnalysis pipeline |
| **Inference Engine** | LiteRT / TFLite Play Services | `play-services-tflite-java`, Memory-mapped `.tflite` |
| **Location Services** | Google Play Services Location | `FusedLocationProviderClient`, High-Accuracy GPS |
| **Local Database** | Room (2.6+) with KSP | SQLite, Flow observables, foreign keys, indices |
| **Background Tasks** | AndroidX WorkManager (2.9+) | `CoroutineWorker`, periodic & one-time sync |
| **Cloud Backend** | Firebase Firestore (BOM) | Document-oriented metadata storage |
| **PDF Generation** | Native Android `PdfDocument` | Multi-page layout, custom Android `Canvas` vector drawing |
| **Unit Testing** | JUnit 4, Kotlinx Coroutines Test | Mockito/In-memory validation, 58 test suite |

---

## 9. AI Model
The on-device inference engine utilizes a customized single-class **YOLO26n** neural network optimized for mobile deployment:

- **Model Asset**: `pothole_detector.tflite`
- **Model Version Identifier**: `RoadTwin-YOLO26n-416-FP32`
- **Input Tensor**: `[1, 416, 416, 3]` (RGB normalized to $[0.0, 1.0]$, FP32)
- **Output Tensor**: `[1, 300, 6]` (End-to-end NMS-free output: `[x1, y1, x2, y2, confidence, classId]`)
- **Model Size**: $\approx 9.1\text{ MB}$ (uncompressed `.tflite` directly mapped from APK assets)
- **Parameters**: $\approx 2.375\text{ Million}$
- **Computational Complexity**: $\approx 5.3\text{ GFLOPs}$
- **Default Confidence Threshold**: $0.40$ ($40\%$)
- **NMS IoU Threshold**: $0.45$ ($45\%$)

---

## 10. Dataset
The model was trained and evaluated on specialized asphalt surface defect datasets focused on single-class pothole identification:
- **Class Label**: `pothole` (Single-class detection)
- **Annotation Format**: Bounding box coordinates normalized to image dimensions
- **Augmentation & Conditions**: Varied daylight scenarios, wet and dry pavement textures, shadowed asphalt, and differing road surfaces (urban streets, state highways, and rural roads).

---

## 11. Road Damage Detection Pipeline
The detection workflow processes incoming camera frames through five rigorous verification steps:

1. **Letterbox Transformation**: The camera feed (`Bitmap`) is scaled while maintaining its original aspect ratio and padded with constant fill (`#727272` / RGB 114) into a $416 \times 416$ square.
2. **Model Inference**: The normalized buffer is evaluated by the LiteRT interpreter, outputting up to 300 candidate bounding boxes with confidence scores.
3. **Coordinate Un-letterboxing**: Detected box coordinates are mapped back to the original camera coordinate space.
4. **Centroid & IoU Tracking**: Detections are correlated with existing active tracks based on Intersection over Union ($\text{IoU} \ge 0.15$) and downward vehicle motion dynamics.
5. **Stability Confirmation (3+ Frames)**: A pothole is confirmed only after being observed in at least **3 consecutive frames** (`ModelConfig.minStableFrames = 3`), eliminating transient false triggers.
6. **Spatial & Temporal Duplicate Suppression**: If a confirmed defect falls within $3.0\text{ meters}$ of a recently logged pothole and exceeds $300\text{ ms}$, it is suppressed to prevent counting the same defect multiple times.

---

## 12. Real-Time GPS and Route Tracking
Accurate geographical attribution is essential for municipal road asset management:
- **Location Client**: `FusedLocationProviderClient` operating with `Priority.PRIORITY_HIGH_ACCURACY`.
- **Update Frequency**: 1-second intervals with continuous GPS callbacks during active sessions.
- **Start Location**: Extracted strictly from the **first valid GPS fix** received after tapping "START MONITORING" and persisted into `monitoring_sessions.startLatitude/startLongitude`.
- **End Location**: Extracted strictly from the **last valid GPS fix** received when tapping "STOP MONITORING".
- **Real Distance Accumulation**: Calculated point-to-point between consecutive fixes using the Haversine formula; does not register movement when stationary.
- **Breadcrumb Trail**: Route coordinates exceeding a 2-meter displacement filter are saved directly to the `route_points` table with accuracy and timestamp metrics.
- **Zero Coordinate Fabrication**: If GPS is temporarily obstructed (e.g., tunnels, covered bridges), the system displays `"GPS location unavailable"` rather than substituting default or synthetic coordinates.

---

## 13. Offline-First Data Architecture
RoadTwin AI adheres to a strict **Offline-First** design pattern:
- **Room as the Single Source of Truth**: All session metadata, detections, and route breadcrumbs are written to the local SQLite database before any remote action is attempted.
- **Zero Cloud Reliance During Driving**: Pothole detection, GPS path tracking, and evidence storage execute entirely on the local device without sending network requests during an active survey.
- **Decoupled Synchronization**: Remote synchronization occurs asynchronously via WorkManager or manual user trigger and does not block or impact the user experience.

---

## 14. Room Database
The local database schema consists of three indexed SQLite tables managed via Room:

### `monitoring_sessions`
| Column | Type | Description |
|---|---|---|
| `sessionId` | String (PK) | Session identifier (e.g., `S-1001`) |
| `title`, `notes`, `remarks` | String | Session annotations and operator notes |
| `startTime`, `endTime` | Long | Epoch timestamps of session start and stop |
| `startLatitude`, `startLongitude` | Double | First valid GPS coordinates |
| `endLatitude`, `endLongitude` | Double | Final valid GPS coordinates |
| `distanceKm` | Double | Accumulated physical distance traveled |
| `totalPotholes` | Int | Total number of confirmed detections |
| `critical/high/medium/low` | Int | Aggregated counts by severity level |
| `status` | String | `ACTIVE`, `COMPLETED`, or `CANCELLED` |
| `syncStatus` | String | `NOT_SYNCED`, `PENDING_UPLOAD`, `UPLOADING`, `SYNCED` |
| `pdfLocalPath` | String | Local filesystem path to generated PDF report |

### `detections`
| Column | Type | Description |
|---|---|---|
| `id` | Int (PK, Auto) | Internal SQLite primary key |
| `detectionId` | String | Formatted identifier (e.g., `D-1001`) |
| `sessionId` | String (FK/Index)| Foreign reference to parent session |
| `latitude`, `longitude` | Double | GPS fix at time of detection |
| `gpsAccuracy` | Float | GPS horizontal accuracy radius ($\pm\text{meters}$) |
| `confidence` | Float | YOLO model confidence score ($0.0 - 1.0$) |
| `severity` | String | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `severitySource` | String | `HEURISTIC` or `MANUAL_OVERRIDE` |
| `imagePath` | String | Absolute path to local JPEG photo crop |
| `modelName`, `modelVersion` | String | Model provenance metadata |

### `route_points`
| Column | Type | Description |
|---|---|---|
| `id` | Long (PK, Auto) | Internal breadcrumb sequence ID |
| `sessionId` | String (FK/Index)| Foreign reference to parent session |
| `latitude`, `longitude` | Double | Intermediate GPS breadcrumb coordinates |
| `accuracy` | Float | Location accuracy at waypoint |
| `timestamp` | Long | Epoch timestamp of waypoint record |

---

## 15. Local Evidence Image Storage
To maintain high performance and avoid expensive cloud storage costs:
- When a pothole track is confirmed stable, the corresponding camera frame crop is written to private internal storage:  
  `context.filesDir/roadtwin/reports/{sessionId}/{detectionId}.jpg`
- Images are compressed as standard high-quality JPEGs.
- Local image paths are stored in the `detections` table and read directly by `PdfReportGenerator` when building inspection reports.
- **Evidence images remain on device storage** and are not uploaded to cloud storage buckets.

---

## 16. PDF Report Generation
The `PdfReportGenerator` compiles a standardized 4-stage municipal engineering report formatted on standard A4 pages ($595 \times 842\text{ pt}$):

### Page 1: Executive Inspection Summary
- **Municipal Header**: Official organization title, report generation timestamp, and system insignia.
- **Session Telemetry Card**: Session ID, date, status (`COMPLETED`), duration, distance traveled, and total defects.
- **Dynamic Road Health Score**: Visual score ($0 - 100$) with rated badge (*Excellent*, *Good*, *Moderate*, *Poor*, *Critical*) and progress bar.
- **Defect Metrics Grid**: Count cards for Critical, High, Medium, and Low severity defects.
- **Municipal Action Recommendation**: High-level maintenance directive tailored to the most severe defect identified.

### Page 2: Route & Road Condition Analysis
- **Start & End Telemetry Cards**: Start and end addresses, GPS coordinates, accuracy tolerances, and timestamps.
- **Vector Canvas Route Map**: 
  - Dynamic scale-to-fit vector rendering of the actual GPS trajectory.
  - Green start marker (`🟢 Start`), Red stop marker (`🔴 End`), and RoadTwin Blue polyline (`#1E88E5`).
  - Defect pins plotted at their respective GPS coordinates.
  - North direction indicator (`▲ N`) and coordinate scale grid.
- **Severity Distribution Bar**: Proportional color-coded visual breakdown of defect severities.

### Page 3+: Pothole Detection Evidence Logs
- Compact 2-card layout per page ($3 + \lceil N/2 \rceil$ total pages).
- Displays genuine on-device camera crops with bounding boxes.
- Detection metadata: Confidence percentage, GPS coordinates, timestamp, and severity rating.
- Municipal remediation directives (e.g., *Hot-mix asphalt patch within 24–48 hours*).

### Final Page: Technical Specifications & Data Integrity Audit
- AI detection engine specifications (YOLO26n, FP32, $416 \times 416$, 3-frame confirmation, 3m spatial suppression).
- Validated research benchmarks.
- Verification checklist confirming 1-to-1 data integrity between Room database records and report contents.

---

## 17. Firestore Metadata Synchronization
RoadTwin AI supports lightweight cloud synchronization with Firebase Firestore:
- **Collection**: `reports/{sessionId}`
- **Payload Structure**: Structured JSON document containing session summary, severity distributions, route point array, and detection metadata.
- **Zero Image Uploads**: Image binaries remain on the device, ensuring fast synchronization even on 2G/3G cellular connections.
- **Status Safeguards**: Active sessions (`status = "ACTIVE"`) or sessions without valid completion timestamps cannot be uploaded, preserving database integrity.

---

## 18. WorkManager Synchronization
Background synchronization is managed by `SyncWorker` (subclass of `CoroutineWorker`):
- **Network Constraints**: Requires an active internet connection (`NetworkType.CONNECTED`).
- **Immediate One-Time Sync**: Triggered upon session completion or manual user request.
- **Periodic Sync**: Automatically scheduled on app startup to run every 1 hour (`PeriodicWorkRequestBuilder`), uploading any pending reports accumulated while offline.

---

## 19. Application Screens
The app features 14 organized screens built with Jetpack Compose:

| Route Key | Screen Name | Description |
|---|---|---|
| `Splash` | Splash Screen | Animated branded launch screen verifying device permissions |
| `Onboarding` | Onboarding Screen | Feature walkthrough explaining windshield mounting and GPS setup |
| `Dashboard` | Home Dashboard | Main hub with "START MONITORING" hero card, road health score, and stats |
| `LiveCamera` | Live Camera AI HUD | Landscape driving interface showing camera stream, real-time boxes, speed, and GPS lock |
| `ReportSummary` | Session Summary | Post-survey summary showing distance, total defects, and export options |
| `EditReport` | Edit Report Screen | Allows adding custom notes, road names, and operator remarks |
| `MapView` | Interactive Map | Map display showing surveyed routes and defect markers |
| `ReportsHistory`| Reports History | Searchable list of past monitoring sessions with sync status indicators |
| `ReportDetail` | Report Detail View | Detailed inspection breakdown with individual defect logs |
| `GeneratePdf` | PDF Generation Screen| PDF preview and export screen with share, view, and print actions |
| `Settings` | Settings Screen | Configuration for confidence thresholds, GPS accuracy limits, and sync |
| `Benchmarks` | Model Benchmarks | Displays technical AI specs, latency, and research evaluation metrics |
| `Profile` | Operator Profile | Operator credentials, municipal agency details, and inspection statistics |
| `About` | About Screen | Version info, open source licenses, privacy policy, and system architecture |

---

## 20. Project Structure
```
pothole-app/
├── README.md                                    # Project documentation
├── .gitignore                                   # Version control exclusions
│
└── android/                                     # Android project root
    ├── build.gradle.kts                         # Root build script
    ├── settings.gradle.kts                      # Module settings
    ├── gradle/libs.versions.toml                # Version catalog
    │
    └── app/
        ├── build.gradle.kts                     # App module build configuration
        ├── proguard-rules.pro                   # R8 / Proguard rules
        │
        └── src/
            ├── main/
            │   ├── AndroidManifest.xml          # Permissions, activities, features
            │   ├── assets/
            │   │   └── pothole_detector.tflite  # YOLO26n TFLite model asset
            │   │
            │   ├── java/com/roadtwin/ai/
            │   │   ├── RoadTwinApplication.kt   # Application class & DI container
            │   │   ├── MainActivity.kt          # Single Activity container
            │   │   │
            │   │   ├── core/                    # Core infrastructure
            │   │   │   ├── camera/              # CameraX preview and lifecycle
            │   │   │   ├── location/            # FusedLocationProviderClient wrapper
            │   │   │   ├── pdf/                 # 4-stage municipal PDF generator
            │   │   │   ├── permissions/         # Runtime permission handlers
            │   │   │   └── theme/               # Colors, typography, shapes
            │   │   │
            │   │   ├── data/                    # Data layer
            │   │   │   ├── local/               # Room entities, DAOs, and database
            │   │   │   ├── remote/              # Firebase Firestore sync manager
            │   │   │   ├── repository/          # ReportsRepository implementation
            │   │   │   └── sync/                # WorkManager SyncWorker
            │   │   │
            │   │   ├── feature/                 # UI Feature modules (Compose)
            │   │   │   ├── camera/              # Live camera HUD & ViewModel
            │   │   │   ├── dashboard/           # Home dashboard & ViewModel
            │   │   │   ├── map/                 # Map view & telemetry overlays
            │   │   │   ├── onboarding/          # Splash & onboarding walkthrough
            │   │   │   ├── profile/             # Profile & About screens
            │   │   │   ├── reports/             # Reports list, summary, detail, PDF
            │   │   │   └── settings/            # Settings & Benchmarks screens
            │   │   │
            │   │   ├── ml/                      # Machine learning engine
            │   │   │   ├── inference/           # TFLiteObjectDetector & letterboxing
            │   │   │   ├── model/               # ModelConfig constants
            │   │   │   ├── postprocessing/      # Non-Maximum Suppression (NMS)
            │   │   │   └── tracking/            # DetectionTracker & duplicate filter
            │   │   │
            │   │   └── navigation/              # Navigation keys and composable graph
            │   │
            │   └── res/                         # Android drawables, mipmaps, strings
            │
            └── test/java/com/roadtwin/ai/       # Automated unit test suite
                ├── PdfReportGeneratorTest.kt    # 16 PDF & data integrity tests
                ├── SessionSyncLifecycleTest.kt  # 34 lifecycle & sync invariant tests
                └── RoadTwinUnitTest.kt          # 8 core logic & tracking tests
```

---

## 21. Installation and Setup

### Prerequisites
- **JDK**: Java 17 or higher
- **Android SDK**: Compile SDK 36, Minimum SDK 24 (Android 7.0+)
- **Build Tool**: Gradle 9.1.0 (via included `gradlew` wrapper)
- **Target Device**: Physical Android device with camera and GPS support (recommended for real survey runs) or emulator with GPS simulation.

### Build Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/ArunKumar-123171/pothole-app.git
   cd pothole-app/android
   ```

2. **Configure SDK Path**:
   Ensure `local.properties` exists in the `android/` directory pointing to your Android SDK:
   ```properties
   sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
   ```

3. **Verify Build and Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Assemble Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The resulting APK will be generated at:  
   `android/app/build/outputs/apk/debug/app-debug.apk`

5. **Deploy to Connected Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 22. Configuration Requirements

### Hardware Permissions
The following runtime permissions are declared in `AndroidManifest.xml` and requested upon first launch:
- `android.permission.CAMERA`: Required for real-time video stream analysis.
- `android.permission.ACCESS_FINE_LOCATION`: Required for high-accuracy GPS tracking.
- `android.permission.ACCESS_COARSE_LOCATION`: Approximate network-based fallback.

### Firebase Setup (Optional Cloud Sync)
1. Register an Android project in the [Firebase Console](https://console.firebase.google.com/) with package name:  
   `com.roadtwin.ai`
2. Download `google-services.json` and place it in the `android/app/` folder.
3. Enable **Cloud Firestore** in test or production mode.
4. *Note: If `google-services.json` is omitted, RoadTwin AI runs smoothly in full offline mode.*

---

## 23. Testing and Validation
The project includes a comprehensive suite of **58 automated unit tests** validating core logic, data integrity, and session lifecycles:

```bash
cd android
./gradlew testDebugUnitTest
```

### Test Suites:
1. **`SessionSyncLifecycleTest` (34 Tests)**:
   - Verifies all 23 data-integrity gates.
   - Confirms active sessions cannot be uploaded or finalized.
   - Enforces 1-to-1 parity between Room database entities and Firestore payloads.
   - Ensures start and end locations are never cloned or fabricated.
2. **`PdfReportGeneratorTest` (16 Tests)**:
   - Validates that no PDF contains `0.000000, 0.000000` fallback coordinates.
   - Confirms graceful fallbacks when GPS or images are unavailable.
   - Verifies road health score formulas and defect count aggregations.
3. **`RoadTwinUnitTest` (8 Tests)**:
   - Tests IoU calculation and centroid tracking dynamics.
   - Tests Haversine distance calculations and duplicate suppression windows.

---

## 24. Model Performance / Results

### Research Experiment Benchmarks
Evaluated on the held-out test split of the pothole dataset:

| Metric | Score | Notes |
|---|---|---|
| **Precision** | **80.44%** | Accuracy of positive pothole identifications |
| **Recall** | **69.72%** | Proportion of actual road defects successfully detected |
| **mAP@50** | **77.66%** | Mean Average Precision at $0.50$ IoU threshold |
| **mAP@50:95** | **46.08%** | Strict COCO-standard mAP across thresholds $0.50 - 0.95$ |

### Mobile Runtime Performance (Snapdragon 7s Gen 2 / Similar Mid-Tier Chipset)
- **Average Latency**: $\approx 12\text{ ms}$ (FP32)
- **Effective Inference Rate**: $10 - 15\text{ FPS}$
- **Hardware Acceleration**: Supported via Google Play Services LiteRT NNAPI / GPU delegates

---

## 25. Limitations
- **Lighting Conditions**: Detection accuracy may decrease in low-light environments, severe night glare, or heavy downpours.
- **Windshield Occlusion**: Dirty or obstructed windshields directly impact detection quality.
- **GPS Signal Degradation**: Tall urban canyons, deep tunnels, and underground passages may cause temporary GPS signal loss (gracefully indicated as *"GPS location unavailable"*).
- **Camera Mount Stability**: Excessive vehicle vibration without a secure windshield mount may introduce motion blur in individual frames.

---

## 26. Future Enhancements
- **Multi-Class Defect Recognition**: Expanding the model to distinguish cracks, rutting, manhole covers, and road debris.
- **Crowdsourced Aggregation**: Centralized municipal heatmaps combining survey runs across municipal vehicle fleets.
- **Audio Alerts for Drivers**: Proactive auditory warning beeps when approaching previously mapped critical potholes.
- **Quantization (INT8)**: Reducing model size from $9.1\text{ MB}$ to $\approx 2.5\text{ MB}$ for improved battery life and execution speed on low-end hardware.

---

## 27. Git / Version Control
- **Main Branch**: `main`
- **Remote**: `https://github.com/ArunKumar-123171/pothole-app.git`
- **Commit Guidelines**: Semantic commit messages describing exact architectural enhancements (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`).

---

## 28. Screenshots

> *Placeholder: Official application screenshots and field demonstration photos can be added to an `art/` or `screenshots/` directory.*

```
[ Home Dashboard ]         [ Live Camera AI HUD ]        [ Municipal PDF Report ]
 (Coming Soon)                  (Coming Soon)                  (Coming Soon)
```

---

## 29. Authors & License

### Authors
- **Arun Kumar** — *Project Lead & AI Engineer* — [GitHub](https://github.com/ArunKumar-123171)

### License
This project is developed for educational, municipal research, and road safety monitoring purposes.  
Please refer to the repository root and application About screen for applicable licensing terms.
