# Creditss

> **Credits:** MrLauncher is a modified version of [MojoLauncher](https://github.com/MojoLauncher) by artdeell and the MojoLauncher contributors, licensed under the original license (see `LICENSE`). All credit for the original work goes to them.

---

# MR Launcher

A launcher Minecraft: Java Edition launcher for Android, based on **MojoLauncher** (and the legacy **PojavLauncher** architecture). Built for stability on modern Android and seamless custom OpenGL renderer plugin support.

Developed by **AditzMods**.

---

## Features

- **Custom Rendererenderer Plugin Support:** Full compatibility with external OpenGL wrapper plugins (GL4ES, MobileGlues, Zink, Angle), including native support for the **Mobile Rendered** plugin (`V17+` and `V17-`).
- **Modern Android Compatibility:** Optimized for Android 14, 15, and 16+ with improved file management and scoped storage handling.
- **Built-in Modloader & Utilities:** Supports Vanilla, Forge, Fabric, Quilt, and OptiFine installations, plus direct `.jar` execution, custom on-screen control mapping, and one-tap log sharing.
- **Lightweight Performance:** Stripped of unnecessary bloat to preserve RAM and maintain smooth frame pacing on mid-range and entry-level Android devices.

---

## Downloads & Community

- **Official WhatsApp Channel (Updates & Guides):** [Join WhatsApp Channel](https://whatsapp.com/channel/0029VbEO1q52v1Ilbyazeh2I)
- **Github:** [adityaoffc0-drood](https://github.com/adityaoffc0-droid)

---

## Building from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API Level 34+) & Android NDK
- Gradle 8.x

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/adityaoffc0-droid/MR-Launcher.git
   cd MR-Launcher
   ```

2. Initialize and update submodules (if applicable):
   ```bash
   git submodule update --init --recursive
   ```

3. Build the debug or release APK via Gradle:
   ```bash
   chmod +x gradlew
   ./gradlew assembleDebug
   ```

4. Locate the compiled APK in:
   ```text
   app_pojavlauncher/build/outputs/apk/debug/
   ```

---

## Project Structure

- `app_pojavlauncher/` — Main Android application module, monochrome UI layouts, themes, and launcher logic.
- `jre_lwjgl3glfw/` — LWJGL3 and GLFW runtime bindings for Android.
- `arc_dns_injector/` — Network and DNS resolution utilities.

---

## Credits & Acknowledgments

- **[MojoLauncher](https://github.com/MojoLauncher/MojoLauncher)** — Upstream fork maintaining active Android compatibility and runtime updates.
- **[PojavLauncherTeam](https://github.com/PojavLauncherTeam/PojavLauncher)** — Original creators of the Minecraft: Java Edition launcher for Android.
- **Boardwalk & LWJGL3** — Core JVM and OpenGL bridging libraries.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for full details. In compliance with GPL-3.0, all source code modifications in this fork remain open-source and retain original copyright notices.




