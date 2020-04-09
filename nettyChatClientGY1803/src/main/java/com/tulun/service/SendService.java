package com.tulun.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tulun.contant.EnMsgType;
import com.tulun.netty.ClientHandler;
import com.tulun.util.JsonUtils;
import io.netty.channel.Channel;

import java.io.File;
import java.net.InetAddress;
import java.util.Scanner;

/**
 * 发送服务
 */
public class SendService {
    private Channel channel;
    private String id;
    private String password;
    Scanner scanner = new Scanner(System.in);


    public SendService(Channel channel) {
        this.channel = channel;
    }

    public void sendMsg() {
        scanner.useDelimiter("\n");
        while (true) {
            loginView();
            String line = scanner.nextLine();
            if ("1".equals(line)) {
                //登录操作
                System.out.println("请输入账号：");
                String id = scanner.nextLine();
                System.out.println("请输入密码：");
                String passwd = scanner.nextLine();
                System.out.println("登录操作："+"id："+id+" passwd:"+passwd);
                doLogin(id, passwd);
            } else if ("4".equals(line)) {
                //退出系统
                System.exit(1);
            } else if("2".equals(line)) {
                //注册操作
                System.out.println("请输入账号：");
                String id = scanner.nextLine();
                System.out.println("请输入姓名：");
                String name = scanner.nextLine();
                System.out.println("请输入密码：");
                String passwd = scanner.nextLine();
                System.out.println("请输入邮箱：");
                String mail = scanner.nextLine();
                register(id,name,passwd,mail);
            } else if("3".equals(line)) {
                //忘记密码
                System.out.println("请输入账号：");
                String id = scanner.nextLine();
                System.out.println("请输入邮箱：");
                String mail = scanner.nextLine();
                forgetPasswd(id,mail);
            }
        }
    }

    /**
     * 判断该账号是否存在
     * @param id
     * @return
     */
    public boolean isExist(String id) {
        //封装JSON数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("id",id);
        node.put("type",String.valueOf(EnMsgType.EN_MSG_CHECK_USER_EXIST));
        String msg = node.toString();

        //发送服务端
        channel.writeAndFlush(msg);

        //等待服务端结果：200表示可以注册，不存在该用户，300表示存在该用户，不可以注册
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(code == 300) {
            return true;
        }
        return false;
    }

    /**
     * 忘记密码业务逻辑
     * @param id
     * 首先判断输入的账号,邮箱是否正确，如果正确服务端通过给用户发送邮箱验证码，并且将
     * 该验证码传回客户端，通过和用户输入的客户端进行比较，如果正确，则可以直接进行
     * 修改密码操作。
     */
    public void forgetPasswd(String id,String mail) {
        //判断该账号是否存在
        if(!isExist(id)) {
            System.out.println("没有该账号，请检查后重新输入");
            return;
        }
        this.id= id;
        //封装JSON数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("id",id);
        node.put("mail",mail);
        node.put("type",String.valueOf(EnMsgType.EN_MSG_FORGET_PWD));
        String msg = node.toString();

        //发送到服务端
        channel.writeAndFlush(msg);

        /**
         * 判断处理结果：约定如果code=300则说明输入邮箱错误，如果200,则证明输入邮箱正确，服务器已经发了验证码
         */
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(code == 300) {
            System.out.println("邮箱输入错误");
            return;
        }
        //code==200,则需要用户输入验证码发送至客户端
        System.out.println("验证码已经发送至您的邮箱，请输入正确的验证码：");
        String identifyPwd = scanner.nextLine();
        /**
         * 验证码发送至服务器进行判断，等待服务器的判断结果，
         */
        //封装JSON数据
        ObjectNode node2 = JsonUtils.getObjectNode();
        node2.put("type",String.valueOf(EnMsgType.EN_MSG_IDENTIFY_PWD));
        node2.put("idePwd",identifyPwd);
        node2.put("id",id);
        String msg2 = node2.toString();
        channel.writeAndFlush(msg2);

        /**
         * 约定200则验证码正确，300则验证码错误，400则验证码失效
         */
        int code2 = 0;
        try {
            code2 = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        /**
         * 根据服务器发回来的结果做判断：
         */
        if(code2 == 200) {
            System.out.println("验证码正确");
            /**
             * 修改密码
             */
            String oldPwd;
            String newPwd;
            String idenPwd;
            do{
                System.out.println("请输入新的密码：");
                newPwd = scanner.nextLine();
                System.out.println("请确认新密码：");
                idenPwd = scanner.nextLine();
            } while (!(newPwd.equals(idenPwd)));
            System.out.println("验证成功，开始修改密码");
            modifyPwd(newPwd);
        } else if(code2 == 300) {
            System.out.println("验证码错误");
        } else if(code2 == 400) {
            System.out.println("验证码失效");
        }
    }

    /**
     * 修改密码
     * @param newPwd
     */
    public boolean modifyPwd(String newPwd) {
        //封装json数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("id",this.id);
        node.put("newPwd",newPwd);
        node.put("type",String.valueOf(EnMsgType.EN_MSG_MODIFY_PWD));
        String msg = node.toString();
        System.out.println(msg);
        //通过通道发送json数据
        channel.writeAndFlush(msg);

        //等待阻塞队列接收消息
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(code == 200) {
            System.out.println("修改密码成功");
            return  true;
        } else {
            System.out.println("修改密码失败");
            return false;
        }
    }

    /**
     * 注册业务逻辑
     * @param name
     * @param pwd
     * @param mail
     * 首先需要判断注册的该用户是否存在，不允许存在同名现象
     * 其次再去判断。。。
     */
    public void register(String id,String name,String pwd,String mail) {

        //判断该账号是否存在结果
        if(isExist(id)) {
            System.out.println("该用户已经被注册，注册失败");
            return;
        }

        //可以注册，用json封装详细信息，发送至服务端进行真正的注册
        ObjectNode node2 = JsonUtils.getObjectNode();
        node2.put("id",id);
        node2.put("name",name);
        node2.put("pwd",pwd);
        node2.put("mail",mail);
        node2.put("type",String.valueOf(EnMsgType.EN_MSG_REGISTER));

        //转为字符串发送至服务器
        String msg2 = node2.toString();
        channel.writeAndFlush(msg2);

        /**
         * 等待注册结果，约定200注册成功，300注册失败
         */
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(code == 200) {
            System.out.println("注册成功");
        } else {
            System.out.println("由于数据库原因注册失败， 请联系管理员或重试");
        }
    }

    /**
     * 登录业务逻辑
     * @param id
     * @param pwd
     */
    public void doLogin(String id, String pwd) {
        //封装JSON数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("id", id);
        node.put("pwd", pwd);
        node.put("type", String.valueOf(EnMsgType.EN_MSG_LOGIN));
        String msg = node.toString();

        //发送服务端
        channel.writeAndFlush(msg);

        //等待服务端返回登录结果
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //结果展示：
        if (code == 200) {
            //登录成功
            //记录id，密码
            this.id = id;
            this.password = pwd;
            showMainMemu();
            while (true) {
                String option = scanner.nextLine();
                String[] split = option.split(":");
                if(split[0].equals("modifypwd")) {
                    String oldPwd;
                    String newPwd;
                    String idenPwd;
                    do{
                        System.out.println("请输入旧的密码：");
                        oldPwd = scanner.nextLine();
                        System.out.println("请输入新的密码：");
                        newPwd = scanner.nextLine();
                        System.out.println("请确认新密码：");
                        idenPwd = scanner.nextLine();
                    } while (!(this.password.equals(oldPwd) && newPwd.equals(idenPwd)));
                    System.out.println("验证成功，开始修改密码");
                    boolean isSuccess = modifyPwd(newPwd);
                    if(isSuccess) {
                        System.out.println("修改成功");
                    } else {
                        System.out.println("修改失败");
                    }
                } else if(split[0].equals("getallusers")) {
                    System.out.println("获取所有人员信息");
                    getOnLineUsers();
                } else  if(split[0].equals("all")) {
                    System.out.println("发送群聊消息");
                    String fromUser = this.id;
                    String data = split[1];
                    sendAllMessage(fromUser,data);
                } else if(split[0].equals("sendfile")){
                    System.out.println("发送文件请求");
                    String fromUser = this.id;
                    String toUser = split[1];
                    String filePath = split[2]+":"+split[3];
                    SendFile(fromUser,toUser,filePath);
                } else if(split[0].equals("quit")) {
                    System.out.println("用户下线");
                    exit(id);
                    break;
                } else if(split[0].equals("help")) {
                    System.out.println("查看系统菜单");
                    showMainMemu();
                } else {
                    String fromUser = this.id;
                    String toUser = split[0];
                    String data = split[1];
                    /**
                     * 发送一对一消息
                     */
                    sendOneMessage(fromUser,toUser,data);
                }
            }
        } else {
            //登录失败
            System.out.println("密码或账户错误，请重试");
        }
    }

    /**
     * 发送文件
     * @param fromUser
     * @param toUser
     * @param filePath
     */
    private void SendFile(String fromUser, String toUser, String filePath) {
        /**
         * 封装json
         */
        System.out.println("fromUser"+fromUser+"toUser:"+toUser+"filePath"+filePath);
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("type",String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE));
        node.put("toUser",toUser);

        //发送
        channel.writeAndFlush(node.toString());

        //等待服务端的消息，看对方用户是否在线
        int port = -1;
        try {
            port = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }



        //另起线程进行传输文件
        new SendFileTread(port,"127.0.0.1",new File(filePath)).start();

    }

    /**
     * 发送群聊消息
     * @param fromUser
     * @param data
     */
    private void sendAllMessage(String fromUser, String data) {
        /**
         * 封装json
         */
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("type",String.valueOf(EnMsgType.EN_MSG_CHAT_ALL));
        node.put("fromUser",fromUser);
        node.put("data",data);
        String msg = node.toString();

        //发送至服务端
        channel.writeAndFlush(msg);
    }


    /**
     * 发送一对一消息
     * @param fromUser
     * @param toUser
     * @param data
     */
    private void sendOneMessage(String fromUser,String toUser,String data) {
        /**
         * 封装json
         */
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("type",String.valueOf(EnMsgType.EN_MSG_CHAT));
        node.put("fromUser",fromUser);
        node.put("toUser",toUser);
        node.put("data",data);
        String msg = node.toString();

        //发送至服务端
        channel.writeAndFlush(msg);


        /**
         * 等待发送消息的结果，约定200发送成功，300对方不在线，发送离线消息，400不存在该用户
         */
        int code = 0;
        try {
            code = ClientHandler.queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(code == 200) {
            System.out.println("发送成功");
        } else if(code == 300){
            System.out.println("对方不在线，消息在对方在线时可以看到");
        } else if (code == 400){
            System.out.println("不存在该用户");
        }
    }

    /**
     * 退出消息
     * @param id
     */
    private void exit(String id) {
        //向服务器发送退出消息
        //封装JSON数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("type",String.valueOf(EnMsgType.EN_MSG_OFFLINE));
        node.put("id",id);
        String msg = node.toString();

        //向服务器发送消息
        channel.writeAndFlush(msg);
    }

    /**
     * 获取在线人员信息
     */
    private void getOnLineUsers() {
        //封装JSON数据
        ObjectNode node = JsonUtils.getObjectNode();
        node.put("type",String.valueOf(EnMsgType.EN_MSG_GET_ALL_USERS));
        String msg = node.toString();

        //发送到服务端
        channel.writeAndFlush(msg);

        //等待服务器的列表回应
        String users = null;
        try {
            users = ClientHandler.queue2.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(users!=null) {
            //拿到用户列表数组
            String[] split = users.split(":");
            System.out.print("在线用户有：");
            for (int i = 0; i < split.length; i++) {
                System.out.print(" "+split[i]);
            }
            System.out.println();
        }
    }


    //    用户登录页面
    private void loginView() {
        System.out.println("======================");
        System.out.println("1.登录");
        System.out.println("2.注册");
        System.out.println("3.忘记密码");
        System.out.println("4.退出系统");
        System.out.println("======================");
    }


    /**
     * 主菜单页面
     */
    private void showMainMemu() {
        System.out.println("====================系统使用说明====================");
        System.out.println("                         注：输入多个信息用\":\"分割");
        System.out.println("1.输入modifypwd:username 表示该用户要修改密码");
        System.out.println("2.输入getallusers 表示用户要查询所有人员信息");
        System.out.println("3.输入username:xxx 表示一对一聊天"); //
        System.out.println("4.输入all:xxx 表示发送群聊消息");
        System.out.println("5.输入sendfile:xxx 表示发送文件请求:[sendfile][接收方用户名][发送文件路径]");
        System.out.println("6.输入quit 表示该用户下线，注销当前用户重新登录");
        System.out.println("7.输入help查看系统菜单");
        System.out.println("================================================");
    }
}
