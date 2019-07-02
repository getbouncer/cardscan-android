package com.getbouncer.example;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import com.getbouncer.cardscan.TestingImageReader;

import java.util.LinkedList;


public class TestResourceImages implements TestingImageReader {
    private MediaMetadataRetriever retriever;
    private int time;
    private LinkedList<Bitmap> bitmapQueue = new LinkedList<>();
    private final int queueSize = 5;

    public TestResourceImages(AssetFileDescriptor fd) {
        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());

        new Thread(new Runnable() {
            @Override
            public void run() {
                producer();
            }
        }).start();
    }

    private synchronized void producer() {
        time = 0;

        while (true) {
            while (bitmapQueue.size() >= queueSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Bitmap bm = retriever.getFrameAtTime(time, retriever.OPTION_CLOSEST);
            time += 250000;
            bitmapQueue.push(bm);
            notify();

            if (bm == null) {
                return;
            }
        }
    }

    private synchronized Bitmap consumer() {
        while (bitmapQueue.size() == 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        notify();
        return bitmapQueue.pop();
    }

    @Override
    public Bitmap nextImage() {
        Bitmap bm = consumer();
        if (bm == null) {
            return null;
        }

        double width = bm.getWidth();
        double height = 302.0 * width / 480.0;
        int x = 0;
        int y = (int) Math.round(((double) bm.getHeight()) * 0.5 - height * 0.5);
        return Bitmap.createBitmap(bm, x, y, (int) width, (int) height);
    }
}
