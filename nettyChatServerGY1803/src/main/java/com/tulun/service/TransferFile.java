package com.tulun.service;

import com.tulun.controller.Transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 */
public class TransferFile extends Thread {
    private int fromPort;
    private int toPort;

    public TransferFile(int fromPort,int toPort) {
        this.fromPort = fromPort;
        this.toPort = toPort;
    }

    @Override
    public void run() {
        try {
            ServerSocket from = new ServerSocket(fromPort);
            ServerSocket to = new ServerSocket(toPort);

            Socket fromSocket = from.accept();
            Socket toSocket = to.accept();

            System.out.println("服务器端两端通信建立完成");
            InputStream inputStream = fromSocket.getInputStream();
            OutputStream outputStream = toSocket.getOutputStream();

            byte[] bytes = new byte[1024];
            int length = -1;
            while((length = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes,0,length);
                outputStream.flush();
            }
            fromSocket.close();
            toSocket.close();
            inputStream.close();
            outputStream.close();
            System.out.println("服务器端文件传输完成");
        } catch (IOException e) {
            System.out.println("服务器端传输文件异常");
        }
    }
}
