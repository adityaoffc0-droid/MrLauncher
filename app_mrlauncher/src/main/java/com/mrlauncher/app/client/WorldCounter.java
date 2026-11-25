package com.mrlauncher.app.client;

import com.mrlauncher.app.Tools;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Menghitung berapa kali pemain masuk ke world, dengan membaca baris baru di latestlog.txt.
 * Singleplayer: baris "Starting integrated minecraft server". Multiplayer: baris "Connecting to".
 */
public final class WorldCounter {
    private long mOffset = -1;

    /** Mengembalikan jumlah world baru yang dimasuki sejak pemanggilan sebelumnya. */
    public int poll() {
        File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if (!log.exists()) return 0;
        long length = log.length();
        if (mOffset < 0) {
            mOffset = length;
            return 0;
        }
        if (length < mOffset) mOffset = 0;
        if (length == mOffset) return 0;

        int count = 0;
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
            raf.seek(mOffset);
            byte[] buf = new byte[(int) Math.min(length - mOffset, 256 * 1024)];
            raf.readFully(buf);
            mOffset += buf.length;
            String text = new String(buf, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (line.contains("Starting integrated minecraft server")
                        || line.contains("Connecting to ")) count++;
            }
        } catch (Throwable ignored) {
        }
        return count;
    }
}
