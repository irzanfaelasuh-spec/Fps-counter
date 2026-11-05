package com.fpsplus.vector;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import rikka.shizuku.Shizuku;

/** Owns the official Shizuku UserService connection used by the FPS reader. */
final class ShizukuShellBridge {
    private final Context context;
    private final Shizuku.UserServiceArgs args;
    private volatile ICommandService remote;
    private volatile boolean bindRequested;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = ICommandService.Stub.asInterface(binder);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null;
        }
    };

    ShizukuShellBridge(Context context) {
        this.context = context.getApplicationContext();
        ComponentName component = new ComponentName(this.context, ShellUserService.class);
        args = new Shizuku.UserServiceArgs(component)
                .daemon(false)
                .tag("fps-vector-shell")
                .version(1);
    }

    void bind() {
        if (bindRequested || !Shizuku.pingBinder()) return;
        try {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            bindRequested = true;
            Shizuku.bindUserService(args, connection);
        } catch (Exception e) {
            bindRequested = false;
            remote = null;
        }
    }

    boolean isReady() {
        return remote != null;
    }

    String run(String command) {
        ICommandService service = remote;
        if (service == null) return null;
        try { return service.execute(command); }
        catch (RemoteException | RuntimeException e) { return null; }
    }

    void unbind() {
        ICommandService service = remote;
        remote = null;
        if (service != null) {
            try { service.shutdown(); } catch (Exception ignored) { }
        }
        if (bindRequested) {
            try { Shizuku.unbindUserService(args, connection, true); }
            catch (Exception ignored) { }
        }
        bindRequested = false;
    }
}
