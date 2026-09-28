# OkHttp / Okio pull in a few optional classes that aren't on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup is plain Java and needs nothing special, but keep its
# package-info out of the warning list.
-dontwarn org.jsoup.**

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
