# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\34620\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any custom rules here that might be necessary for your project.
-keep class padelpulseapp2.netlify.app.** { *; }

# Guava (para el ListenableFuture de Health Services): sus anotaciones de
# compilacion no existen en ejecucion y R8 no debe exigirlas.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
