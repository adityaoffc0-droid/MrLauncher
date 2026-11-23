# Patch: impor renderer (.so/.zip) + deteksi plugin APK

Salin isi folder ini ke root proyek (path sudah sesuai), timpa file yang sama.

## Impor
Pengaturan > Video and renderer > "Impor renderer" memilih file .so atau .zip.
Zip boleh berisi folder ABI (arm64-v8a/ dll), dipilih otomatis sesuai perangkat.
Tanpa renderer.json, tipe ditebak dari nama file (mobileglues, gl4es, holygl4es, ltw, zink, freedreno, mesa).

renderer.json (opsional) di dalam zip:
{
  "name": "Zink Custom",
  "library": "libEGL_zink.so",
  "type": "mesa",
  "gles": 3,
  "vulkan": true,
  "env": { "MESA_LOADER_DRIVER_OVERRIDE": "zink" }
}

## Plugin APK
Manifest plugin:
<application android:extractNativeLibs="true">
  <meta-data android:name="mrlauncher.renderer.library" android:value="libmobileglues.so"/>
  <meta-data android:name="mrlauncher.renderer.name" android:value="MobileGlues"/>
  <meta-data android:name="mrlauncher.renderer.type" android:value="gles"/>
  <meta-data android:name="mrlauncher.renderer.gles" android:value="3"/>
  <activity android:name=".Stub" android:exported="true">
    <intent-filter><action android:name="com.mrlauncher.app.RENDERER_PLUGIN"/></intent-filter>
  </activity>
</application>
Opsional: mrlauncher.renderer.vulkan (boolean), mrlauncher.renderer.env ("KEY=VAL;KEY2=VAL2").

## Plugin format Zalith/FCL
Plugin APK dengan meta-data aplikasi fclPlugin=true dan renderer="Nama:libGL.so:libEGL.so"
(opsional pojavEnv="KEY=VAL:KEY2=VAL2") otomatis terdeteksi. Perlu izin QUERY_ALL_PACKAGES.

## Dari mrlaunchersourcecode.zip
- libvulkan_freedreno.so (arm64-v8a) dipasang: dikenali sebagai driver Vulkan "Built-in" (Turnip).
- GL4ES (ES3) ditambahkan memakai libgl4es_114.so yang sudah ada.
- libOSMesa.so dan libgl4es_114.so dari zip itu tidak dipasang (lihat pesan).
