package com.vladsaif.vkmessagestat.utils;

import java.io.Serializable;

/**
 * Created by el-gl on 18.08.2017.
 */
public class Pair<T, S> implements Serializable {
    public T first;
    public S second;

    public Pair(T a, S b) {
        first = a;
        second = b;
    }
}
