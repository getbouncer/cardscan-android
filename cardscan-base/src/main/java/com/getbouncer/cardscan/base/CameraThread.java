package com.getbouncer.cardscan.base;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;

class CameraThread extends Thread {
    private WeakReference<OnCameraOpenListener> listener;

    synchronized void startCamera(OnCameraOpenListener listener) {
        this.listener = new WeakReference<>(listener);
        notify();
    }

    synchronized OnCameraOpenListener waitForOpenRequest() {
        while (this.listener == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return listener.get();
    }

    @Override
    public void run() {
        final OnCameraOpenListener listener = waitForOpenRequest();
        if (listener == null) {
            this.listener.clear();
            return;
        }

        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            Log.e("CameraThread", "failed to open Camera");
            e.printStackTrace();
        }

        final Camera resultCamera = camera;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onCameraOpen(resultCamera);
                    CameraThread.this.listener.clear();
                    CameraThread.this.listener = null;
                }
            });
    }
}
