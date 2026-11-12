# Shizuku starts the user service by class name in another process.
-keep class com.thorergo.core.injection.shizuku.PrivilegedInputService { <init>(...); }
-keep class com.thorergo.core.injection.shizuku.IPrivilegedInput** { *; }
