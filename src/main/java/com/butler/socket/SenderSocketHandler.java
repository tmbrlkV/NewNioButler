package com.butler.socket;

import org.zeromq.ZMQ;

public class SenderSocketHandler implements AutoCloseable {
    private ZMQ.Socket sender;

    public SenderSocketHandler(ZMQ.Context context) {
        sender = context.socket(ZMQ.PUSH);
        sender.connect("tcp://10.66.161.2:10001");
    }

    public void send(String message) {
        sender.send(message);
    }

    @Override
    public void close() throws Exception {
        sender.close();
    }
}
