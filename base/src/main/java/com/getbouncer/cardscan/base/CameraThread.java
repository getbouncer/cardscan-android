package com.getbouncer.cardscan.base;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;

class CameraThread extends Thread {
    private OnCameraOpenListener listener;

    synchronized void startCamera(OnCameraOpenListener listener) {
        this.listener = listener;
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

        OnCameraOpenListener listener = this.listener;
        this.listener = null;
        return listener;
    }

    @Override
    public void run() {
        while (true) {
            final OnCameraOpenListener listener = waitForOpenRequest();
            final Camera camera = Camera.open();
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onCameraOpen(camera);
                }
            });
        }
    }
}
