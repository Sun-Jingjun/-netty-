package com.tulun.service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 *
 */
public class SendFileTread extends Thread {
    private int toPort;
    private String ip;
    private File file;

    public SendFileTread(int toPort, String ip, File file) {
        this.toPort = toPort;
        this.ip = ip;
        this.file = file;
    }

    @Override
    public void run() {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip,toPort));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            //连接完成，开始传输数据
            //获得输入输出流
            FileInputStream fileInputStream = null;
            DataOutputStream dataOutputStream = null;
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            fileInputStream = new FileInputStream(file);
            byte[] bytes = new byte[1024];
            int length = -1;
            dataOutputStream.writeUTF(file.getName());
            while((length = fileInputStream.read(bytes)) != -1) {
                dataOutputStream.write(bytes,0,length);
                dataOutputStream.flush();
            }

            fileInputStream.close();
            dataOutputStream.close();
            socket.close();
            System.out.println("发送方发送文件完成");
        } catch (IOException e) {
            System.out.println("发送方传送失败");
        }
    }
}
