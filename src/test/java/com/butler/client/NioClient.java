package com.butler.client;

import com.butler.server.ChangeRequest;
import com.butler.socket.ConnectionProperties;
import com.butler.util.entity.User;
import com.butler.util.json.JsonMessage;
import com.butler.util.json.JsonObjectFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

public class NioClient implements Runnable {
    private InetAddress hostAddress;
    private int port;
    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final List<ChangeRequest> pendingChanges = new LinkedList<>();
    private final Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<>();
    private Map<SocketChannel, ResponseHandler> responseHandlers = Collections.synchronizedMap(new HashMap<>());

    private NioClient(InetAddress hostAddress, int port) throws IOException {
        this.hostAddress = hostAddress;
        this.port = port;
        this.selector = this.initSelector();
    }

    private void send(byte[] data, ResponseHandler handler) throws IOException {
        SocketChannel socket = initiateConnection();

        responseHandlers.put(socket, handler);

        synchronized (pendingData) {
            List<ByteBuffer> queue = pendingData.get(socket);
            if (queue == null) {
                queue = new ArrayList<>();
                pendingData.put(socket, queue);
            }
            queue.add(ByteBuffer.wrap(data));
        }

        this.selector.wakeup();
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (this.pendingChanges) {
                    for (ChangeRequest change : this.pendingChanges) {
                        switch (change.getType()) {
                            case ChangeRequest.CHANGER:
                                SelectionKey key = change.getSocket().keyFor(this.selector);
                                key.interestOps(change.getOps());
                                break;
                            case ChangeRequest.REGISTER:
                                change.getSocket().register(this.selector, change.getOps());
                                break;
                        }
                    }
                    this.pendingChanges.clear();
                }

                this.selector.select();
                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        this.finishConnection(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        readBuffer.clear();
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            key.channel().close();
            key.cancel();
            return;
        }

        handleResponse(socketChannel, readBuffer.array(), numRead);
    }

    private void handleResponse(SocketChannel socketChannel, byte[] data, int numRead) throws IOException {
        byte[] rspData = new byte[numRead];
        System.arraycopy(data, 0, rspData, 0, numRead);
        ResponseHandler handler = responseHandlers.get(socketChannel);
        if (handler.handleResponse(rspData)) {
            socketChannel.close();
            socketChannel.keyFor(this.selector).cancel();
        }
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            List<ByteBuffer> queue = this.pendingData.get(socketChannel);
            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void finishConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            socketChannel.finishConnect();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            key.cancel();
            return;
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private SocketChannel initiateConnection() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(this.hostAddress, this.port));
        synchronized (this.pendingChanges) {
            this.pendingChanges.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }
        return socketChannel;
    }

    private Selector initSelector() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

    public static void main(String[] args) {
        try {
            Properties properties = ConnectionProperties.getProperties();
            String host = properties.getProperty("butler_address");
            int port = Integer.parseInt(properties.getProperty("butler_port"));
            NioClient client = new NioClient(InetAddress.getByName(host), port);
            Thread t = new Thread(client);
            t.setDaemon(true);
            t.start();
            ResponseHandler handler = new ResponseHandler();
            String jsonString = JsonObjectFactory.getJsonString("getUserByLoginPassword", new User("kek", "kek"));
            client.send(jsonString.getBytes(), handler);
            String reply = handler.waitForResponse();
            User user = JsonObjectFactory.getObjectFromJson(reply, User.class);
            if (user != null) {
                System.out.println(user.getLogin() + " has joined");
                try (Scanner scanner = new Scanner(System.in)) {
                    while (!Thread.currentThread().isInterrupted()) {
                        JsonMessage message = new JsonMessage("message", user.getLogin(), scanner.nextLine());
                        String toSend = JsonObjectFactory.getJsonString(message);
                        client.send(toSend.getBytes(), handler);
                        String response = handler.waitForResponse();
                        JsonMessage jsonMessage = JsonObjectFactory.getObjectFromJson(response, JsonMessage.class);
                        if (jsonMessage != null) {
                            System.out.println(jsonMessage.getUsername() + ": " + jsonMessage.getContent());
                        } else {
                            System.out.println(response);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}