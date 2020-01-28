package com.getbouncer.cardscan.base;

import android.content.Context;

import com.getbouncer.cardscan.base.ModelFactory;

import java.io.IOException;
import java.nio.MappedByteBuffer;

public class ResourceModelFactory extends ModelFactory {

    @Override
    public MappedByteBuffer loadSSDDetectModelFile(Context context) throws IOException{
        return  loadModelFromResource(context, R.raw.ssdelrond0136);
    }

}
