package com.tulun.netty;

import com.tulun.controller.Transfer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.ConcurrentHashMap;

public class ChannelHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("服务器接收到消息："+msg);
        Transfer transfer = new Transfer();
        String recv = transfer.process((String) msg,ctx);
        if(recv!=null) {
            ctx.channel().writeAndFlush(recv);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("连接异常");
        cause.printStackTrace();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if(Transfer.isExist(ctx)) {
            Transfer.removeByChannel(ctx);
        }
        System.out.println("客户端强制关闭");
    }
}
