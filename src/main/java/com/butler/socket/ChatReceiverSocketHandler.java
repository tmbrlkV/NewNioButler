package com.butler.socket;

import com.butler.server.ServerDataEvent;
import org.zeromq.ZMQ;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ChatReceiverSocketHandler implements Runnable {
    private static final int CUTOFF = 50;
    private ZMQ.Socket receiver;
    private ZMQ.Poller poller;
    private List<ServerDataEvent> clients = new CopyOnWriteArrayList<>();

    public ChatReceiverSocketHandler(ZMQ.Context context) {
        receiver = context.socket(ZMQ.SUB);
        receiver.connect("tcp://10.66.161.2:10000");
        receiver.subscribe("".getBytes());
        poller = new ZMQ.Poller(0);
        poller.register(receiver, ZMQ.Poller.POLLIN);
    }

    public void addClient(ServerDataEvent dataEvent) {
        clients.add(dataEvent);
    }

    public void removeClient(ServerDataEvent dataEvent) {
        clients.remove(dataEvent);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            int events = poller.poll();
            if (events > 0) {
                String reply = receiver.recvStr();
                Consumer<ServerDataEvent> handler = data -> data.getServer().send(data.getSocket(), reply.getBytes());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (clients.size() > CUTOFF) {
                    clients.parallelStream().forEach(handler);
                } else {
                    clients.forEach(handler);
                }
            }
        }
    }
}
