package com.fpsplus.vector;

import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Runs only fixed, read-only diagnostic commands under Shizuku's user-service identity. */
public final class ShellUserService extends ICommandService.Stub {
    @Override public String execute(String command) throws RemoteException {
        java.lang.Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.redirectErrorStream(true);
            process = builder.start();
            process.getOutputStream().close();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 450_000) output.append(line).append('\n');
                }
            }
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return output.toString();
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return null;
        }
    }

    @Override public void shutdown() throws RemoteException {
        new Thread(() -> {
            try { Thread.sleep(350L); } catch (InterruptedException ignored) { }
            android.os.Process.killProcess(android.os.Process.myPid());
        }, "FPSVector-Shutdown").start();
    }
}
