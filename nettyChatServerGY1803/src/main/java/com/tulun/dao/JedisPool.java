package com.tulun.dao;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class JedisPool {
    private static redis.clients.jedis.JedisPool jedisPool;
    static {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("redis.properties");
        Properties properties = new Properties();
        try {
            properties.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String host = properties.getProperty("redis.host");
        int port = Integer.parseInt(properties.getProperty("redis.port"));
        int max_total = Integer.parseInt(properties.getProperty("redis.max_total"));
        int min_Idle = Integer.parseInt(properties.getProperty("redis.Min_Idle"));
        int max_Idle = Integer.parseInt(properties.getProperty("redis.Max_Idle"));
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(max_total);
        poolConfig.setMinIdle(min_Idle);
        poolConfig.setMaxIdle(max_Idle);
        jedisPool = new redis.clients.jedis.JedisPool(poolConfig,host,port);
    }
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }

    public static void closeJedis(Jedis jedis){
        if(jedis!=null){
            jedis.close();
        }
    }
}