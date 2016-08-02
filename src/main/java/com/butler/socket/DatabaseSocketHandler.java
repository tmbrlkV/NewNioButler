package com.butler.socket;

import org.zeromq.ZMQ;

public class DatabaseSocketHandler implements AutoCloseable {
    private ZMQ.Socket requester;

    public DatabaseSocketHandler(ZMQ.Context context) {
        requester = context.socket(ZMQ.REQ);
        requester.connect("tcp://10.66.161.2:11000");
    }

    public void send(String message) {
        requester.send(message);
    }

    public String receive() {
        return requester.recvStr();
    }

    @Override
    public void close() {
        requester.close();
    }
}
