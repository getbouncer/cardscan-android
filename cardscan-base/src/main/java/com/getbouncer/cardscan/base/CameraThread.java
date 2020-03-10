package com.getbouncer.cardscan.base;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

class CameraThread extends Thread {
    @Nullable private WeakReference<OnCameraOpenListener> mListener;

    synchronized void startCamera(OnCameraOpenListener listener) {
        this.mListener = new WeakReference<>(listener);
        notify();
    }

    @Nullable
    synchronized OnCameraOpenListener waitForOpenRequest() {
        while (this.mListener == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return mListener.get();
    }

    @Override
    public void run() {
        final OnCameraOpenListener listener = waitForOpenRequest();
        if (listener == null && this.mListener != null) {
            this.mListener.clear();
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
                    if (listener != null) {
                        listener.onCameraOpen(resultCamera);
                    }
                    if (mListener != null) {
                        mListener.clear();
                    }
                    mListener = null;
                }
            });
    }
}
