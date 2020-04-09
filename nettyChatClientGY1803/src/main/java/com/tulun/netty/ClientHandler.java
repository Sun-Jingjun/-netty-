package com.tulun.netty;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tulun.contant.EnMsgType;
import com.tulun.service.RecvFileThread;
import com.tulun.util.JsonUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import sun.awt.image.IntegerInterleavedRaster;

import java.util.concurrent.SynchronousQueue;

public class ClientHandler extends SimpleChannelInboundHandler<String> {
    //同步阻塞队列，将服务端返回给工作线程的数据传输给主线程处理
    public static SynchronousQueue<Integer> queue = new SynchronousQueue <>();
    public static SynchronousQueue<String> queue2 = new SynchronousQueue <>();
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println(ctx.channel().remoteAddress() + " 2: " + msg);
        //解析服务端返回数据
        String recvMsg = (String) msg;
        ObjectNode jsonNodes = JsonUtils.getObjectNode(recvMsg);
        String type = jsonNodes.get("type").asText();
        if (String.valueOf(EnMsgType.EN_MSG_ACK).equals(type)) {
            //ack消息
            String srctype = jsonNodes.get("srctype").asText();
            if (String.valueOf(EnMsgType.EN_MSG_LOGIN).equals(srctype)) {
                //登录操作的ack消息
                int code = jsonNodes.get("code").asInt();
                //将服务端的消息返回交给主线程
                queue.put(code);
            } else if (String.valueOf(EnMsgType.EN_MSG_CHECK_USER_EXIST).equals(srctype)) {
                //判断用户是否存在的ack消息
                int code = jsonNodes.get("code").asInt();
                //将服务器的消息返回主线程处理
                queue.put(code);
            } else if (String.valueOf(EnMsgType.EN_MSG_REGISTER).equals(srctype)) {
                //判断用户是否注册成功
                int code = jsonNodes.get("code").asInt();
                //将结果返回主线程处理
                queue.put(code);
            } else if (String.valueOf(EnMsgType.EN_MSG_FORGET_PWD).equals(srctype)) {
                //忘记密码
                int code = jsonNodes.get("code").asInt();
                //将结果返回主线程处理
                queue.put(code);
            } else if (String.valueOf(EnMsgType.EN_MSG_CHAT).equals(srctype)) {
                //判断客户端发送消息是否成功
                int code = jsonNodes.get("code").asInt();
                //将结果返回主线程处理
                queue.put(code);
            } else if(String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE).equals(srctype)) {
                //发送文件：对方在线，且服务器端返回了可用的端口进行连接
                int port = jsonNodes.get("port").asInt();
                queue.put(port);
            }
        } else if (String.valueOf(EnMsgType.EN_MSG_MODIFY_PWD).equals(type)) {
            //修改密码消息
            int code = jsonNodes.get("code").asInt();
            //将服务器消息返回主线程处理
            queue.put(code);
        } else if (String.valueOf(EnMsgType.EN_MSG_IDENTIFY_PWD).equals(type)) {
            //验证码消息
            int code = jsonNodes.get("code").asInt();
            queue.put(code);
        } else if (String.valueOf(EnMsgType.EN_MSG_GET_ALL_USERS).equals(type)) {
            //获得所有在线用户信息
            String users = jsonNodes.get("users").asText();
            queue2.put(users);
        } else if (String.valueOf(EnMsgType.EN_MSG_CHAT).equals(type)) {
            //客户端接收消息的类型
            String fromUser = jsonNodes.get("fromUser").asText();
            String data = jsonNodes.get("data").asText();
            System.out.println(fromUser + " : " + data);
        } else if(String.valueOf(EnMsgType.EN_MSG_OFFLINE_MSG).equals(type)) {
            //接收离线消息
            System.out.println("您有离线消息需要接收");
            String off_msg = jsonNodes.get("msg").asText();
            String[] split = off_msg.split("#");
            for (int i = 0; i < split.length; i++) {
                System.out.println(split[i]);
            }
        } else if(String.valueOf(EnMsgType.EN_MSG_CHAT_ALL).equals(type)) {
            //群发消息
            String fromUser = jsonNodes.get("fromUser").asText();
            String allData = jsonNodes.get("data").asText();
            System.out.println(fromUser+":"+allData);
        } else if(String.valueOf(EnMsgType.EN_MSG_NOTIFY_ONLINE).equals(type)) {
            //用户上线
            String id = jsonNodes.get("id").asText();
            System.out.println("用户:"+id+"上线了");
        } else if(String.valueOf(EnMsgType.EN_MSG_NOTIFY_OFFLINE).equals(type)) {
            //用户下线
            String id = jsonNodes.get("id").asText();
            System.out.println("用户:"+id+"下线了");
        } else if(String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE).equals(type)) {
            //接收方接收文件消息
            int port = jsonNodes.get("port").asInt();
            new RecvFileThread(port,"127.0.0.1").start();
        }
    }
}
