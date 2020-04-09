package com.tulun.util;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashSet;
import java.util.Random;

/**
 *    端口服务类：提供可用的端口
 */
public class PortUtils {
    //繁忙的端口
    private static HashSet<Integer> busyPorts = new HashSet<>();

    //提供端口
    public static int getFreePort() {
        Random random = new Random();
        int port;
        while(true) {
            //随机生成空闲端口
            port = random.nextInt(65535-5000)+5000;
            //判断该端口是否在占用队列中
            if(busyPorts.contains(port)) {
                continue;
            } else {
                //判断该端口是否真实可以用，因为一部分端口被占用，但是并未收集
                try {
                    new DatagramSocket(port);
                } catch (Exception e) {
                    continue;
                }
                //测试通过，返回端口
                return port;
            }
        }
    }

    //返回端口
    public static void backPort(int port) {
        if(busyPorts.contains(port)) {
            busyPorts.remove(port);
        }
    }
}
