# NovaBrowser (Android Module)

> A security-first, local-first browser architecture that keeps users safer by default and enables on-device contextual retrieval without cloud dependency.

*For the complete architecture, security specifications, data models, and build instructions, see the primary [Project README](../README.md).*

---

## Quick Reference

- **Core Module (`:browser-core`):** Pure Kotlin implementation of the Deterministic Security Gate (`UrlCanonicalizer`, `HeuristicsEngine`, `RedirectTracker`, `ThreatFeedManager`, `SecurityGate`) and SQLite database helper.
- **Application Shell (`:app`):** Android application implementing the Liquid System UI/UX, multi-tab lifecycle (`TabManager`), sandboxed WebView runtime (`NovaWebView`), and explainable security warning interstitials.
- **Intelligence Stub (`:ai`):** Dynamic device tier detection (`DeviceTierDetector`) and upcoming `llama.cpp` / GGUF local model execution module.

### Compiling Debug APK

```powershell
# From this directory:
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
.\gradlew.bat assembleDebug --offline
```

### Running Security Verification

```powershell
powershell -ExecutionPolicy Bypass -File .\run_tests.ps1
```
