# Shizuku starts the user service by class name in another process.
-keep class jr.brian.thorkbm.core.injection.shizuku.PrivilegedInputService { <init>(...); }
-keep class jr.brian.thorkbm.core.injection.shizuku.IPrivilegedInput** { *; }
