package com.getbouncer.cardscan.base.ssd;


import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class ArrayIndexComparator implements Comparator<Integer>
{
    @NonNull
    private final Float[] array;

    public ArrayIndexComparator(@NotNull Float[] array)
    {
        this.array = array;
    }

    @NonNull
    public Integer[] createIndexArray()
    {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
        {
            indexes[i] = i; // Autoboxing
        }
        return indexes;
    }

    @Override
    public int compare(@NonNull Integer index1, @NonNull Integer index2)
    {
         // Autounbox from Integer to int to use as array indexes
        return Float.compare(array[index1], array[index2]) * (-1);
    }
}
