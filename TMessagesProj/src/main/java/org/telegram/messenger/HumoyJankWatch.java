/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.os.Looper;
import android.os.SystemClock;

/**
 * Debug builds only: when the main thread is busy with one message for longer than {@link #THRESHOLD_MS},
 * writes what it is doing (its stack, sampled while still busy) to the log as "HUMOY_JANK", to find
 * what makes screens stutter.
 */
public class HumoyJankWatch {

    private static final long THRESHOLD_MS = 80;
    private static volatile long messageStart;
    private static volatile int messageSerial;
    private static boolean started;

    public static void start() {
        if (started || !BuildVars.LOGS_ENABLED) {
            return;
        }
        started = true;
        Looper.getMainLooper().setMessageLogging(line -> {
            if (line.startsWith(">>>>> Dispatching")) {
                messageSerial++;
                messageStart = SystemClock.elapsedRealtime();
            } else if (line.startsWith("<<<<< Finished")) {
                long took = SystemClock.elapsedRealtime() - messageStart;
                messageStart = 0;
                if (took >= THRESHOLD_MS) {
                    FileLog.d("HUMOY_JANK done " + took + "ms");
                }
            }
        });
        Thread main = Looper.getMainLooper().getThread();
        Thread watcher = new Thread(() -> {
            int sampledSerial = -1;
            while (true) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    return;
                }
                long start = messageStart;
                int serial = messageSerial;
                if (start != 0 && serial != sampledSerial && SystemClock.elapsedRealtime() - start >= THRESHOLD_MS) {
                    sampledSerial = serial;
                    StringBuilder sb = new StringBuilder("HUMOY_JANK busy ").append(SystemClock.elapsedRealtime() - start).append("ms\n");
                    StackTraceElement[] stack = main.getStackTrace();
                    for (int i = 0; i < Math.min(stack.length, 40); i++) {
                        sb.append("    at ").append(stack[i]).append('\n');
                    }
                    FileLog.d(sb.toString());
                }
            }
        }, "HumoyJankWatch");
        watcher.setDaemon(true);
        watcher.start();
    }
}
