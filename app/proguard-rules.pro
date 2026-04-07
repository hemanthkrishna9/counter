# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ViewBinding
-keep class com.mantracounter.app.databinding.** { *; }

# App classes
-keep class com.mantracounter.app.** { *; }

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Suppress common warnings
-dontwarn java.lang.invoke.**
-dontwarn org.jetbrains.annotations.**
