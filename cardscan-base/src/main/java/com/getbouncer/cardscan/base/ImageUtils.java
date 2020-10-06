package com.getbouncer.cardscan.base;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageUtils {

    @NonNull
    public static Bitmap drawBoxesOnImage(
            @NonNull Bitmap frame,
            @Nullable List<DetectedBox> boxes,
            @Nullable DetectedBox expiryBox
    ) {
        Paint paint = new Paint(0);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);

        Bitmap mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        if (boxes != null) {
            for (DetectedBox box : boxes) {
                canvas.drawRect(box.rect.RectF(), paint);
            }
        }

        paint.setColor(Color.RED);
        if (expiryBox != null) {
            canvas.drawRect(expiryBox.rect.RectF(), paint);
        }

        return mutableBitmap;
    }

    /**
     * Takes a relation between a region of interest and a rect and projects the region of interest
     * to that new location
     */
    public static Rect projectRegionOfInterest(
        @NonNull final Rect fromRect,
        @NonNull final Rect toRect,
        @NonNull final Rect regionOfInterest
    ) {
        return new Rect(
            (int) ((float) (regionOfInterest.left - fromRect.left)) * toRect.width() / fromRect.width() + toRect.left,
            (int) ((float) (regionOfInterest.top - fromRect.top)) * toRect.height() / fromRect.height() + toRect.top,
            (int) ((float) (regionOfInterest.right - fromRect.left)) * toRect.width() / fromRect.width() + toRect.left,
            (int) ((float) (regionOfInterest.bottom - fromRect.top)) * toRect.height() / fromRect.height() + toRect.top
        );
    }

    /**
     * Given a size and an aspect ratio, resize the area to fit that aspect ratio. If the desired aspect
     * ratio is smaller than the one of the provided size, the size will be cropped to match. If the
     * desired aspect ratio is larger than the that of the provided size, then the size will be expanded
     * to match.
     */
    public static Rect adjustSizeToAspectRatio(
        final int width,
        final int height,
        final float aspectRatio
    ) {
        if (aspectRatio < 1) {
            return new Rect(0, 0, width, (int) (width / aspectRatio));
        } else {
            return new Rect(0, 0, (int) (height * aspectRatio), height);
        }
    }

    /**
     * Crops and image using originalImageRect and places it on finalImageRect, which is filled with
     * gray for the best results
     */
    public static Bitmap cropWithFill(final Bitmap bitmap, final Rect cropRegion) {
        final Rect intersectionRegion = intersectionOf(rectOf(bitmap), cropRegion);
        final Bitmap result = Bitmap.createBitmap(
            cropRegion.width(),
            cropRegion.height(),
            bitmap.getConfig()
        );
        final Canvas canvas = new Canvas(result);

        canvas.drawColor(Color.GRAY);

        final Bitmap croppedImage = crop(bitmap, intersectionRegion);

        canvas.drawBitmap(
            croppedImage,
            rectOf(croppedImage),
            move(intersectionRegion, -cropRegion.left, -cropRegion.top),
            null
        );

        return result;
    }

    public static Rect rectOf(final Bitmap bitmap) {
        return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    }

    public static Rect intersectionOf(final Rect rect1, final Rect rect2) {
        return new Rect(
            Math.max(rect1.left, rect2.left),
            Math.max(rect1.top, rect2.top),
            Math.min(rect1.right, rect2.right),
            Math.min(rect1.bottom, rect2.bottom)
        );
    }

    public static Bitmap crop(final Bitmap bitmap, final Rect crop) {
        return Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height());
    }

    public static Bitmap scale(final Bitmap bitmap, final int newWidth, final int newHeight) {
        if (newWidth == bitmap.getWidth() && newHeight == bitmap.getHeight()) {
            return bitmap;
        } else {
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
        }
    }

    public static Rect move(final Rect rect, int x, int y) {
        return new Rect(
            rect.left + x,
            rect.top + y,
            rect.right + x,
            rect.bottom + y
        );
    }

    public static Rect center(final Rect rect, final Rect on) {
        return new Rect(
            /* left */
            on.centerX() - rect.width() / 2,
            /* top */
            on.centerY() - rect.height() / 2,
            /* right */
            on.centerX() + rect.width() / 2,
            /* bottom */
            on.centerY() + rect.height() / 2
        );
    }

    public static Bitmap zoom(
        final Bitmap bitmap,
        final Rect regionToZoom,
        final Rect newZoomRegion,
        final int newImageWidth,
        final int newImageHeight
    ) {
        // Produces a map of rects to rects which are used to map segments of the old image onto the
        // new one
        final Map<Rect, Rect> regionMap = resizeRegion(
            bitmap.getWidth(),
            bitmap.getHeight(),
            regionToZoom,
            newZoomRegion,
            newImageWidth,
            newImageHeight
        );

        // construct the bitmap from the region map
        return rearrangeBySegments(bitmap, regionMap);
    }


    /**
     * This method allows relocating and resizing a portion of a [Size]. It returns the required
     * translations required to achieve this relocation. This is useful for zooming in on sections
     * of an image.
     *
     * For example, given a size 5x5 and an original region (2, 2, 3, 3):
     *
     *  _______
     * |       |
     * |   _   |
     * |  |_|  |
     * |       |
     * |_______|
     *
     * If the [newRegion] is (1, 1, 4, 4) and the [newSize] is 6x6, the result will look like this:
     *
     *  ________
     * |  ___   |
     * | |   |  |
     * | |   |  |
     * | |___|  |
     * |        |
     * |________|
     *
     * Nine individual translations will be returned for the affected regions. The returned [Rect]s
     * will look like this:
     *
     *  ________
     * |_|___|__|
     * | |   |  |
     * | |   |  |
     * |_|___|__|
     * | |   |  |
     * |_|___|__|
     */
    public static Map<Rect, Rect> resizeRegion(
        final int originalWidth,
        final int originalHeight,
        final Rect originalRegion,
        final Rect newRegion,
        final int newWidth,
        final int newHeight
    ) {
        return new HashMap<Rect, Rect>() {{
            put(
                new Rect(
                    0,
                    0,
                    originalRegion.left,
                    originalRegion.top
                ), new Rect(
                    0,
                    0,
                    newRegion.left,
                    newRegion.top
                )
            );

            put(
                new Rect(
                    originalRegion.left,
                    0,
                    originalRegion.right,
                    originalRegion.top
                ), new Rect(
                    newRegion.left,
                    0,
                    newRegion.right,
                    newRegion.top
                )
            );

            put(
                new Rect(
                    originalRegion.right,
                    0,
                    originalWidth,
                    originalRegion.top
                ), new Rect(
                    newRegion.right,
                    0,
                    newWidth,
                    newRegion.top
                )
            );

            put(
                new Rect(
                    0,
                    originalRegion.top,
                    originalRegion.left,
                    originalRegion.bottom
                ), new Rect(
                    0,
                    newRegion.top,
                    newRegion.left,
                    newRegion.bottom
                )
            );

            put(
                new Rect(
                    originalRegion.left,
                    originalRegion.top,
                    originalRegion.right,
                    originalRegion.bottom
                ), new Rect(
                    newRegion.left,
                    newRegion.top,
                    newRegion.right,
                    newRegion.bottom
                )
            );

            put(
                new Rect(
                    originalRegion.right,
                    originalRegion.top,
                    originalWidth,
                    originalRegion.bottom
                ), new Rect(
                    newRegion.right,
                    newRegion.top,
                    newWidth,
                    newRegion.bottom
                )
            );

            put(
                new Rect(
                    0,
                    originalRegion.bottom,
                    originalRegion.left,
                    originalHeight
                ), new Rect(
                    0,
                    newRegion.bottom,
                    newRegion.left,
                    newHeight
                )
            );

            put(
                new Rect(
                    originalRegion.left,
                    originalRegion.bottom,
                    originalRegion.right,
                    originalHeight
                ), new Rect(
                    newRegion.left,
                    newRegion.bottom,
                    newRegion.right,
                    newHeight
                )
            );

            put(
                new Rect(
                    originalRegion.right,
                    originalRegion.bottom,
                    originalWidth,
                    originalHeight
                ), new Rect(
                    newRegion.right,
                    newRegion.bottom,
                    newWidth,
                    newHeight
                )
            );
        }};
    }

    public static Bitmap rearrangeBySegments(
        final Bitmap bitmap,
        final Map<Rect, Rect> segmentMap
    ) {
        Rect newImageDimensions = null;
        for (Map.Entry<Rect, Rect> entry : segmentMap.entrySet()) {
            if (newImageDimensions == null) {
                newImageDimensions = entry.getValue();
            } else {
                newImageDimensions = new Rect(
                    Math.min(newImageDimensions.left, entry.getValue().left),
                    Math.min(newImageDimensions.top, entry.getValue().top),
                    Math.max(newImageDimensions.right, entry.getValue().right),
                    Math.max(newImageDimensions.bottom, entry.getValue().bottom)
                );
            }
        }

        if (newImageDimensions == null) {
            return Bitmap.createBitmap(0, 0, bitmap.getConfig());
        }

        final Bitmap result = Bitmap.createBitmap(
            newImageDimensions.width(),
            newImageDimensions.height(),
            bitmap.getConfig()
        );
        final Canvas canvas = new Canvas(result);

        for (Map.Entry<Rect, Rect> entry : segmentMap.entrySet()) {
            final Rect from = entry.getKey();
            final Rect to = move(
                entry.getValue(),
                -newImageDimensions.left,
                -newImageDimensions.top
            );

            final Bitmap segment = scale(crop(bitmap, from), to.width(), to.height());
            canvas.drawBitmap(segment, to.left, to.top, null);
        }

        return result;
    }
}
