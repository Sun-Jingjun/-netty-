package com.tulun.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.tulun.cantant.EnMsgType;
import com.tulun.dao.C3p0Instance;
import com.tulun.dao.JedisPool;
import com.tulun.mail.Mail;
import com.tulun.netty.ChannelHandler;
import com.tulun.service.TransferFile;
import com.tulun.util.JsonUtils;
import com.tulun.util.PortUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import redis.clients.jedis.Jedis;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Transfer {
    //用于存储用户在线信息的hashmap,存储格式为： id，channel
    private static  ConcurrentHashMap<Integer, ChannelHandlerContext> hashMap1 = new ConcurrentHashMap<>();
    //用于存储用户在线信息的hashmap，存储格式为：channel，id
    private static ConcurrentHashMap<ChannelHandlerContext,Integer> hashMap2 = new ConcurrentHashMap<>();
    //消息解析器
    public String process(String msg, ChannelHandlerContext channel) {
        ObjectNode objectNode = JsonUtils.getObjectNode(msg);
        //解析数据类型
        String type = objectNode.get("type").asText();
        if (String.valueOf(EnMsgType.EN_MSG_LOGIN).equals(type)) {
            //登录请求
            String id = objectNode.get("id").asText();
            String pwd = objectNode.get("pwd").asText();
            System.out.println("登录操作：id"+id+"， passwd:"+pwd);

            //封装返回数据类型
            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type", String.valueOf(EnMsgType.EN_MSG_ACK));
            nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_LOGIN));
            boolean success = false;
            if(isSuccess(id,pwd)) {
                //数据库操作判断登录是否成功,成功状态返回200，不成功返回300
                nodes.put("code", 200);
                hashMap1.put(Integer.parseInt(id),channel);
                hashMap2.put(channel,Integer.parseInt(id));
                success = true;
            } else {
                nodes.put("code",300);
                System.out.println("300");
            }
            //登录回复消息
            String recvMsg = nodes.toString();
            channel.channel().writeAndFlush(recvMsg);

            //如果登录成功的话取出并发送离线消息
            if(success) {
                //取出离线消息,给该用户发送离线消息
                String offMsg = getOffMsg(id);
                System.out.println("offMsg"+offMsg);
                if(offMsg != null) {
                    ObjectNode nodes2 = JsonUtils.getObjectNode();
                    nodes2.put("type",String.valueOf(EnMsgType.EN_MSG_OFFLINE_MSG));
                    nodes2.put("msg",offMsg);
                    String off_msg = nodes2.toString();;

                    channel.channel().writeAndFlush(off_msg);
                }
                //提醒其他用户，该用户上线了
                remindOthersOn(id);
            }
            return null;
        } else if(String.valueOf(EnMsgType.EN_MSG_CHECK_USER_EXIST).equals(type)) {
            //是否存在该用户请求
            String id = objectNode.get("id").asText();
            System.out.println("是否存在该用户："+id);
            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type", String.valueOf(EnMsgType.EN_MSG_ACK));
            nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_CHECK_USER_EXIST));
            if(isExistUser(id)) {
                nodes.put("code",300);
            } else {
                nodes.put("code",200);
            }
            String recvMsg = nodes.toString();
            return recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_REGISTER).equals(type)) {
            //进行注册
            String id = objectNode.get("id").asText();
            String name = objectNode.get("name").asText();
            String pwd = objectNode.get("pwd").asText();
            String mail = objectNode.get("mail").asText();
            System.out.println("注册："+id+" : "+name+" : "+pwd+" : "+mail+" : ");
            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
            nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_REGISTER));
            if(doRegister(id,name,pwd,mail)) {
                nodes.put("code",200);
            } else {
                /**
                 * ???注册失败的情况有哪些？？？
                 */
                nodes.put("code",300);
            }
            String recvMsg = nodes.toString();
            return  recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_FORGET_PWD).equals(type)) {
            //忘记密码
            String id = objectNode.get("id").asText();
            String mail = objectNode.get("mail").asText();
            System.out.println("忘记密码："+id+mail);

            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
            nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_FORGET_PWD));
            
            /**
             * 判断邮箱与账号是否匹配,不匹配返回300
             */
            if(!isMatch(id,mail)) {
                nodes.put("code",300);
            } else {
                 //匹配上，进行忘记密码业务处理
                forgetPWD(id,mail);
                nodes.put("code",200);
            }
            String recvMsg = nodes.toString();
            return recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_MODIFY_PWD).equals(type)) {
            //修改密码消息
            String id = objectNode.get("id").asText();
            String newPwd = objectNode.get("newPwd").asText();


            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type",String.valueOf(EnMsgType.EN_MSG_MODIFY_PWD));
            if(modifyPwd(id,newPwd)) {
                nodes.put("code",200);
            } else {
                nodes.put("code",300);
            }
            String recvMsg = nodes.toString();
            return recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_IDENTIFY_PWD).equals(type)) {
            //验证码校验请求
            //从客户端拿到验证码
            String idePwd = objectNode.get("idePwd").asText();
            String id = objectNode.get("id").asText();

            /**
             * 从redis中拿到一条连接jedis
             */
            Jedis jedis = JedisPool.getJedis();

            //封装信息
            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type",String.valueOf(EnMsgType.EN_MSG_IDENTIFY_PWD));


            //先判断是否过时？
            Boolean exists = jedis.exists(id);
            String idePwdRedis = jedis.get(id);
            if(exists && idePwd.equals(idePwdRedis)) {
                //用户的输入正确,封装200
                nodes.put("code",200);
            } else if(exists){
                nodes.put("code",300);
            }
            if(!exists) {
                //超时，封装400
                nodes.put("code",400);
            }
            String recvMsg = nodes.toString();
            return recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_GET_ALL_USERS).equals(type)) {
            //获取所有在线用户信息
            //封装信息
            ObjectNode nodes = JsonUtils.getObjectNode();
            nodes.put("type",String.valueOf(EnMsgType.EN_MSG_GET_ALL_USERS));
            StringBuilder builder = new StringBuilder();
            int count = 0;
            for (Integer i : hashMap1.keySet()) {
                if(count != 0) {
                    builder.append(":");
                }
                builder.append(i);
                count++;
            }
            String users = builder.toString();
            nodes.put("users",users);
            String recvMsg = nodes.toString();
            return recvMsg;
        } else if(String.valueOf(EnMsgType.EN_MSG_OFFLINE).equals(type)) {
            //下线消息

            //解析json数据
            int id = objectNode.get("id").asInt();

            //用户下线消息
            //直接从缓冲中去掉该条记录。
            removeById(id);
        } else if(String.valueOf(EnMsgType.EN_MSG_CHAT).equals(type)) {
            System.out.println("一对一聊天信息 ");
            //一对一聊天消息

            //解析json数据
            String fromUser = objectNode.get("fromUser").asText();
            String toUser = objectNode.get("toUser").asText();
            String data = objectNode.get("data").asText();

            //判断要发送的用户是否在线
            if(isOnline(toUser)) {
                //在线直接转发消息
                ChannelHandlerContext channelToUser = hashMap1.get(Integer.parseInt(toUser));
                channelToUser.channel().writeAndFlush(msg);

                //给发送方回复发送成功
                //封装返回数据类型
                ObjectNode nodes = JsonUtils.getObjectNode();
                nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
                nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_CHAT));
                nodes.put("code",200);
                String recvMsg = nodes.toString();

                channel.channel().writeAndFlush(recvMsg);
            } else if(isExistUser(fromUser)){
                //不在线存储该消息到数据库
                storeMsg(fromUser,toUser,data);

                //封装返回数据类型
                ObjectNode nodes = JsonUtils.getObjectNode();
                nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
                nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_CHAT));
                nodes.put("code",300);
                String recvMsg = nodes.toString();

                channel.channel().writeAndFlush(recvMsg);
            } else {
                //不存在该用户

                //封装返回数据类型
                ObjectNode nodes = JsonUtils.getObjectNode();
                nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
                nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_CHAT));
                nodes.put("code",400);
                String recvMsg = nodes.toString();

                channel.channel().writeAndFlush(recvMsg);
            }
        } else if(String.valueOf(EnMsgType.EN_MSG_CHAT_ALL).equals(type)) {
            //群聊消息
            String id = objectNode.get("fromUser").asText();
            Set<Map.Entry<ChannelHandlerContext, Integer>> channels = hashMap2.entrySet();
            Iterator<Map.Entry<ChannelHandlerContext, Integer>> iterator = channels.iterator();
            while(iterator.hasNext()) {
                Map.Entry<ChannelHandlerContext, Integer> next = iterator.next();
                Integer value = next.getValue();
                if(value != Integer.parseInt(id)) {
                    ChannelHandlerContext key = next.getKey();
                    key.channel().writeAndFlush(msg);
                }
            }
        } else if(String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE).equals(type)) {
            //尝试发送文件
            String toUser = objectNode.get("toUser").asText();
            boolean online = isOnline(toUser);
            if(online) {
                //用户在线，可以发送文件，给发送方客户端返回一个可以连接的端口
                int fromPort = PortUtils.getFreePort();
                int toPort = PortUtils.getFreePort();

                //服务端子线程启动
                new TransferFile(fromPort,toPort).start();



                //给接收方发送消息
                ChannelHandlerContext channelHandlerContext = hashMap1.get(Integer.parseInt(toUser));
                Channel toChannel = channelHandlerContext.channel();
                ObjectNode nodes2 = JsonUtils.getObjectNode();
                nodes2.put("type",String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE));
                nodes2.put("port",toPort);
                String recv2 = nodes2.toString();
                toChannel.writeAndFlush(recv2);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //给发送方发送消息
                ObjectNode nodes = JsonUtils.getObjectNode();
                nodes.put("type",String.valueOf(EnMsgType.EN_MSG_ACK));
                nodes.put("srctype",String.valueOf(EnMsgType.EN_MSG_TRANSFER_FILE));
                nodes.put("port",fromPort);
                String recv = nodes.toString();
                channel.channel().writeAndFlush(recv);


            } else {
                //接收方不在线

            }
        }
        return "";
    }

    /**
     * 提醒其他用户，该用户上线了
     * @param id
     */
    private void remindOthersOn(String id) {
        Set<Map.Entry<ChannelHandlerContext, Integer>> channels = hashMap2.entrySet();
        Iterator<Map.Entry<ChannelHandlerContext, Integer>> iterator = channels.iterator();

        //封装返回数据类型
        ObjectNode nodes = JsonUtils.getObjectNode();
        nodes.put("type",String.valueOf(EnMsgType.EN_MSG_NOTIFY_ONLINE));
        nodes.put("id",id);
        String msg = nodes.toString();

        while(iterator.hasNext()) {
            Map.Entry<ChannelHandlerContext, Integer> next = iterator.next();
            Integer value = next.getValue();
            if(value != Integer.parseInt(id)) {
                ChannelHandlerContext key = next.getKey();
                key.channel().writeAndFlush(msg);
            }
        }
    }

    /**
     * 从数据库中取出离线消息
     */
    private String getOffMsg(String id) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<Integer> integers = new ArrayList<>();
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "SELECT * FROM off_msg WHERE toUser=? AND state=1";
            statement = connection.prepareStatement(sql);
            statement.setString(1,id);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String fromUser = resultSet.getString(1);
                String data = resultSet.getString(3);
                stringBuilder.append(fromUser);
                stringBuilder.append(":");
                stringBuilder.append(data);
                stringBuilder.append("#");
                integers.add(Integer.parseInt(resultSet.getString(5)));
            }

            //设置状态
            Iterator<Integer> iterator = integers.iterator();
            while(iterator.hasNext()) {
                Integer num = iterator.next();
                sql = "UPDATE off_msg SET state=0 WHERE id=?";
                statement = connection.prepareStatement(sql);
                statement.setString(1,String.valueOf(num));
                statement.executeUpdate();
            }

            //返回离线消息列表
            if(stringBuilder.length() != 0) {
                return stringBuilder.toString();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void storeMsg(String fromUser, String toUser, String data) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "INSERT INTO off_msg(fromUser, toUser, data, state) VALUES (?,?,?,?)";
            statement = connection.prepareStatement(sql);
            statement.setString(1,fromUser);
            statement.setString(2,toUser);
            statement.setString(3,data);
            statement.setString(4,String.valueOf(1));
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private boolean isOnline(String toUser) {
        return hashMap1.containsKey(Integer.parseInt(toUser));
    }

    private boolean modifyPwd(String id, String newPwd) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "UPDATE user SET pwd=? WHERE id=?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1,newPwd);
            preparedStatement.setString(2,id);
            int i = preparedStatement.executeUpdate();
            if(i==1) {
                System.out.println("1");
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 发送验证码到指定邮箱
     * @param id
     * @param mail
     */
    private void forgetPWD(String id,String mail) {
        /**
         * 生成验证码
         */
        int identify = new Random().nextInt(1000000);

        /**
         * 获得一个jedis连接
         */
        Jedis jedis = JedisPool.getJedis();
        jedis.set(id,String.valueOf(identify));

        /**
         * 发送验证码到指定邮箱
         */
        try {
            Mail.sendMail(mail,String.valueOf(identify));
            jedis.expire(id,40);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 判断名字与mail是否匹配
     * @param id
     * @param mail
     * @return
     */
    private boolean isMatch(String id, String mail) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "SELECT * FROM user WHERE id=? AND email=?";
            statement = connection.prepareStatement(sql);
            statement.setString(1,id);
            statement.setString(2,mail);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if(resultSet!=null) {
                    resultSet.close();
                }
                if(statement!=null) {
                    statement.close();
                }
                if(connection!=null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 进行注册业务
     * @param name
     * @param pwd
     * @param mail
     * @return
     */
    private boolean doRegister(String id,String name, String pwd, String mail) {
        Connection connection = null;
        PreparedStatement statement = null;
        try{
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "INSERT INTO user VALUES(?,?,?,?)";
            statement = connection.prepareStatement(sql);
            statement.setString(1,id);
            statement.setString(2,name);
            statement.setString(3,pwd);
            statement.setString(4,mail);
            int execute = statement.executeUpdate();
            if(execute > 0) {
                System.out.println("服务端注册成功");
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if(statement!=null) {
                    statement.close();
                }
                if(connection!=null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 判断登录用户密码是否正确
     * @param id
     * @param pwd
     * @return
     */
    public boolean isSuccess(String id,String pwd) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "select * from user where id=? and pwd=?";
            statement = connection.prepareStatement(sql);
            statement.setString(1,id);
            statement.setString(2,pwd);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } catch ( SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if(resultSet!=null) {
                    resultSet.close();
                }
                if(statement!=null) {
                    statement.close();
                }
                if(connection!=null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 判断是否存在该用户
     * @param id
     * @return
     */
    public boolean isExistUser(String id) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = C3p0Instance.getDataSource().getConnection();
            String sql = "SELECT * FROM user WHERE id=?";
            statement = connection.prepareStatement(sql);
            statement.setString(1,id);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } catch ( SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if(resultSet!=null) {
                    resultSet.close();
                }
                if(statement!=null) {
                    statement.close();
                }
                if(connection!=null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 从缓存中删除一组连接，通过id
     */
    public static void removeById(Integer id) {
        ChannelHandlerContext channel = hashMap1.get(id);
        hashMap1.remove(id);
        hashMap2.remove(channel);
        /**
         * 向所有人发送该id下线了
         */
        remindOthersOff(String.valueOf(id));
    }

    /**
     * 从缓存中删除一组连接，通过channel
     */
    public static void removeByChannel(ChannelHandlerContext channel) {
        Integer id = hashMap2.get(channel);
        hashMap1.remove(id);
        hashMap2.remove(channel);
        /**
         * 向所有人发送该id下线了
         */
        remindOthersOff(String.valueOf(id));
    }

    public static void remindOthersOff(String id) {
        Set<Map.Entry<ChannelHandlerContext, Integer>> channels = hashMap2.entrySet();
        Iterator<Map.Entry<ChannelHandlerContext, Integer>> iterator = channels.iterator();

        //封装返回数据类型
        ObjectNode nodes = JsonUtils.getObjectNode();
        nodes.put("type",String.valueOf(EnMsgType.EN_MSG_NOTIFY_OFFLINE));
        nodes.put("id",id);
        String msg = nodes.toString();

        while(iterator.hasNext()) {
            Map.Entry<ChannelHandlerContext, Integer> next = iterator.next();
            Integer value = next.getValue();
            if(value != Integer.parseInt(id)) {
                ChannelHandlerContext key = next.getKey();
                key.channel().writeAndFlush(msg);
            }
        }
    }

    /**
     * 通过通道判断该用户是否存在
     */
    public static boolean isExist(ChannelHandlerContext channel){
        return hashMap2.containsKey(channel);
    }
}
