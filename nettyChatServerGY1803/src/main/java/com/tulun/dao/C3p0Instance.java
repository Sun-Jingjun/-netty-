package com.tulun.dao;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 *
 */
public class C3p0Instance {
    private static ComboPooledDataSource dataSource;
    private C3p0Instance() {

    }
    public static ComboPooledDataSource getDataSource() {
        if(dataSource == null) {
            synchronized (C3p0Instance.class) {
                if(dataSource == null) {
                    dataSource = new ComboPooledDataSource("online");
                }
            }
        }
        return  dataSource;
    }
}
