package com.getbouncer.cardscan.base.ssd;


import java.util.Comparator;

public class ArrayIndexComparator implements Comparator<Integer>
{
    private final Float[] array;

    public ArrayIndexComparator(Float[] array)
    {
        this.array = array;
    }

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
    public int compare(Integer index1, Integer index2)
    {
         // Autounbox from Integer to int to use as array indexes
        return Float.compare(array[index1], array[index2]) * (-1);
    }
}
