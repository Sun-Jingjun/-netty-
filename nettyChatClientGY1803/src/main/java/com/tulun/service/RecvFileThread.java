package com.tulun.service;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 *
 */
public class RecvFileThread extends Thread {
    private int port;
    private String ip;
    public RecvFileThread(int port,String ip) {
        this.port = port;
        this.ip = ip;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip,port));

            System.out.println("接收方文件通道连接成功");
            //获取输入输出流
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            //拿到文件名称
            String fileName = dataInputStream.readUTF();
            FileOutputStream fileOutputStream = new FileOutputStream(new File("E:\\b"+"\\"+fileName));
            //传输文件
            byte[] bytes = new byte[1024];
            int length = -1;
            while((length=dataInputStream.read(bytes)) != -1) {
                fileOutputStream.write(bytes,0,length);
                fileOutputStream.flush();
            }
            fileOutputStream.close();
            dataInputStream.close();
            socket.close();
            System.out.println("接收方接收文件完成");
        } catch (IOException e) {
            System.out.println("接收方传输失败");
        }
    }
}
