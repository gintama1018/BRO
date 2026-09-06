# NovaBrowser Production R8 / Proguard Configuration

# 1. Preserve Core Domain & Security Models
-keep class com.gintama.novabrowser.core.model.** { *; }
-keep class com.gintama.novabrowser.core.security.** { *; }
-keep class com.gintama.novabrowser.core.db.** { *; }

# 2. Preserve Kotlin Coroutines Runtime
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# 3. Preserve Android WebView & WebChromeClient Callback Signatures
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebView {
    public *;
}

# 4. ViewBinding & Material Design Components
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static * bind(android.view.View);
}
-keep class com.google.android.material.** { *; }

# 5. Cryptography & Security Providers
-keepclassmembers class * extends java.security.Provider {
    public *;
}
