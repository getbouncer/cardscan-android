package com.getbouncer.example;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.getbouncer.cardscan.TestingImageReader;

import java.util.LinkedList;


public class TestResourceImages implements TestingImageReader {
    private LinkedList<Integer> frames = new LinkedList<>();
    private Resources resources;

    public TestResourceImages(Resources resources) {
        this.resources = resources;
        frames.push(R.drawable.frame0);
        frames.push(R.drawable.frame19);
        frames.push(R.drawable.frame38);
        frames.push(R.drawable.frame57);
        frames.push(R.drawable.frame73);
        frames.push(R.drawable.frame76);
        frames.push(R.drawable.frame95);
        frames.push(R.drawable.frame99);
        frames.push(R.drawable.frame114);
        frames.push(R.drawable.frame133);
    }

    @Override
    public Bitmap nextImage() {
        if (frames.size() == 0) {
            return null;
        }

        int resourceId = frames.pop();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bm = BitmapFactory.decodeResource(resources, resourceId, options);

        double width = bm.getWidth();
        double height = 302.0 * width / 480.0;
        int x = 0;
        int y = (int) Math.round(((double) bm.getHeight()) * 0.5 - height * 0.5);
        return Bitmap.createBitmap(bm, x, y, (int) width, (int) height);
    }
}
