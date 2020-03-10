package com.getbouncer.cardscan.base;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import androidx.annotation.NonNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public abstract class ModelFactory {
    @NonNull
    public abstract MappedByteBuffer loadModelFile(@NonNull Context context) throws IOException;

    private static ModelFactory sharedInstance;

    @NonNull
    public MappedByteBuffer loadModelFromResource(@NonNull Context context, int resource) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getResources()
                .openRawResourceFd(resource);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer result = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset,
                declaredLength);
        inputStream.close();
        fileDescriptor.close();
        return result;
    }

    @NonNull
    public static ModelFactory getSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new ResourceModelFactory();
        }

        return sharedInstance;
    }
}
