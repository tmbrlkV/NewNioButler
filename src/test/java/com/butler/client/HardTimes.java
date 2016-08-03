package com.butler.client;

import com.butler.socket.ConnectionProperties;
import com.butler.util.entity.User;
import com.butler.util.json.JsonMessage;
import com.butler.util.json.JsonObjectFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class HardTimes {
    private static int[] counter;

    public static void main(String[] args) throws IOException {
        Properties properties = ConnectionProperties.getProperties();
        String host = properties.getProperty("butler_address");
        int port = Integer.parseInt(properties.getProperty("butler_port"));
        List<Thread> clientThreads = new CopyOnWriteArrayList<>();
        counter = new int[]{0};
        for (int i = 0; i < 1_000; ++i) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    counter[0]++;
                    NioClient client = new NioClient(InetAddress.getByName(host), port);
                    Thread clientThread = new Thread(client);
                    clientThread.setDaemon(true);
                    clientThread.start();
                    clientThreads.add(clientThread);
                    ResponseHandler handler = new ResponseHandler();
                    String jsonString = JsonObjectFactory.getJsonString("newUser", new User(counter[0] + "", counter[0] + ""));
                    client.send(jsonString.getBytes(), handler);
                    String reply = handler.waitForResponse();
                    User user = JsonObjectFactory.getObjectFromJson(reply, User.class);
                    if (user != null) {
                        System.out.println(user.getLogin() + " " + user.getPassword() + " SIGN UP");
                    }
                    return client;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).thenApplyAsync(client -> {
                try {
                    if (client != null) {
                        ResponseHandler handler = new ResponseHandler();
                        String jsonString = JsonObjectFactory.getJsonString("getUserByLoginPassword", new User(counter[0] + "", counter[0] + ""));
                        client.send(jsonString.getBytes(), handler);
                        String reply = handler.waitForResponse();
                        User user = JsonObjectFactory.getObjectFromJson(reply, User.class);
                        if (user != null) {
                            System.out.println(user.getLogin() + " " + user.getPassword() + " SIGN IN");
                            return client;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).thenApplyAsync(client -> {
                try {
                    if (client != null) {
                        for (int j = 0; j < 10; ++j) {
                            ResponseHandler handler = new ResponseHandler();
                            JsonMessage message = new JsonMessage("message", counter[0] + "", "kek");
                            String toSend = JsonObjectFactory.getJsonString(message);
                            client.send(toSend.getBytes(), handler);
                            String response = handler.waitForResponse();
                            JsonMessage jsonMessage = JsonObjectFactory.getObjectFromJson(response, JsonMessage.class);
                            if (jsonMessage != null) {
                                System.out.println(jsonMessage.getUsername() + ": " + jsonMessage.getContent());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).join();
        }
        clientThreads.forEach(Thread::interrupt);
    }
}
