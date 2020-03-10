package com.getbouncer.cardscan.base;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.MappedByteBuffer;

public class ResourceModelFactory extends ModelFactory {

    @NonNull
    @Override
    public MappedByteBuffer loadModelFile(@NonNull Context context) throws IOException{
        return loadModelFromResource(context, R.raw.darknite);
    }

}
