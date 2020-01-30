package com.getbouncer.cardscan.base;

import android.content.Context;

import com.getbouncer.cardscan.base.ModelFactory;

import java.io.IOException;
import java.nio.MappedByteBuffer;

class ResourceModelFactory extends ModelFactory {

    @Override
    public MappedByteBuffer loadSSDDetectModelFile(Context context) throws IOException{
        return  loadModelFromResource(context, R.raw.darknite);
    }

}
