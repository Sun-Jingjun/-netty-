package com.tulun;

import com.tulun.netty.NettyServer;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        NettyServer nettyServer = new NettyServer();
        nettyServer.init(6666);
    }
}
