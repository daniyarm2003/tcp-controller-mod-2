package com.lildan42.mods.tcpcontrollermod2;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.BiFunction;

public class TCPServer {

    private static final InetSocketAddress ADDRESS = new InetSocketAddress(6969);
    private static final Charset ENCODING = StandardCharsets.UTF_16LE;
    private static final long TIMEOUT = 1L;
    private static final int PACKET_SIZE = 1024;

    private static final int COMMAND_ID_SIZE = 1, DATA_LENGTH_SIZE = 4;

    private ServerSocketChannel server;
    private Selector selector;

    private HashMap<SocketChannel, SocketDataInfo> clientMap;
    private HashMap<Integer, BiFunction<Level, String[], String>> commands;

    public TCPServer() {
        try {
            this.clientMap = new HashMap<>();
            this.commands = new HashMap<>();

            this.server = ServerSocketChannel.open();
            this.server.configureBlocking(false);
            this.server.bind(ADDRESS);

            this.selector = Selector.open();
            this.server.register(this.selector, SelectionKey.OP_ACCEPT);

            this.registerCommands();
        }
        catch(Exception exc) {
            exc.printStackTrace();
            this.stop();
        }
    }

    private void registerCommands() {
        for(Method method : TCPCommandClass.class.getMethods()) {
            TCPCommand commandAnn = method.getAnnotation(TCPCommand.class);

            if(commandAnn == null)
                continue;

            BiFunction<Level, String[], String> commandExec = (level, args) -> {
                if(args.length < commandAnn.minArgs() || args.length > commandAnn.maxArgs())
                    return "Argument count must be within the range of %d and %d".formatted(commandAnn.minArgs(), commandAnn.maxArgs());

                try {
                    return (String) method.invoke(null, level, args);
                }
                catch(Exception e) {
                    e.printStackTrace();
                    return "Unexpected Error: " + e.getMessage();
                }
            };

            this.commands.put(commandAnn.id(), commandExec);
        }
    }

    public void loop(Level level) {
        try {
            if(this.selector.select(TIMEOUT) == 0)
                return;

            Iterator<SelectionKey> keyIterator = this.selector.selectedKeys().iterator();
            while(keyIterator.hasNext()) {

                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if(!key.isValid())
                    continue;

                if(key.isAcceptable())
                    this.accept(key, level);

                if(key.isReadable()) {
                    try {
                        this.read(key, level);
                    }
                    catch(IOException e) {
                        this.closeConnection(key, level);
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                        this.closeConnection(key, level);
                    }
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            this.stop();
        }
    }

    private void stop() {
        try {
            TCPControllerMod2.LOGGER.info("TCP Server closing...");

            this.selector.close();
            this.server.socket().close();
            this.server.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void accept(SelectionKey key, Level level) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        clientChannel.configureBlocking(false);
        clientChannel.register(this.selector, SelectionKey.OP_READ);

        InetSocketAddress address = (InetSocketAddress) clientChannel.getRemoteAddress();

        this.broadcastMessage(level, "Client %s:%d connected.".formatted(address.getHostString(), address.getPort()));

        this.clientMap.put(clientChannel, new SocketDataInfo());
    }

    private void read(SelectionKey key, Level level) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer dataBuffer = ByteBuffer.allocate(PACKET_SIZE);

        int bytesRead = -1;

        try {
            bytesRead = clientChannel.read(dataBuffer);
        }
        catch(IOException e) {
            e.printStackTrace();
        }

        if(bytesRead == -1)
            throw new IOException("Client disconnected.");

        byte[] data = new byte[bytesRead];

        dataBuffer.flip();
        dataBuffer.get(data);

        SocketDataInfo socketData = this.clientMap.get(clientChannel);
        int headerSize = COMMAND_ID_SIZE + DATA_LENGTH_SIZE;

        if(socketData.currentIndex < headerSize) {
            socketData.extendData(bytesRead);
            socketData.appendData(data, 0, bytesRead);

            if(socketData.currentIndex >= headerSize) {
                int dataLength = 0;

                for(int i = 0; i < DATA_LENGTH_SIZE; i++) {
                    dataLength |= Byte.toUnsignedInt(socketData.data[COMMAND_ID_SIZE + i]) << (8 * i);
                }

                if(socketData.data.length > dataLength) {
                    byte[] excessData = new byte[socketData.data.length - dataLength];
                    System.arraycopy(socketData.data, dataLength, excessData, 0, excessData.length);

                    TCPControllerMod2.LOGGER.info(new String(excessData, StandardCharsets.UTF_8));
                }

                socketData.extendData(dataLength - socketData.data.length);
            }
        }
        else {
            if(bytesRead > socketData.data.length - socketData.currentIndex) {
                byte[] excessData = new byte[bytesRead - socketData.data.length + socketData.currentIndex];
                System.arraycopy(data, socketData.data.length - socketData.currentIndex, excessData, 0, excessData.length);

                TCPControllerMod2.LOGGER.info(new String(excessData, StandardCharsets.UTF_8));
            }

            socketData.appendData(data, 0, Math.min(bytesRead, socketData.data.length - socketData.currentIndex));
        }

        if(socketData.currentIndex >= headerSize && socketData.currentIndex == socketData.data.length) {
            byte[] completeData = socketData.data.clone();
            socketData.reset();

            this.onFinishReading(clientChannel, completeData, level);
        }
    }

    private void onFinishReading(SocketChannel clientChannel, byte[] data, Level level) {
        String strData = new String(data, COMMAND_ID_SIZE + DATA_LENGTH_SIZE, data.length - COMMAND_ID_SIZE - DATA_LENGTH_SIZE, ENCODING);
        String[] args = strData.split("\\s+");

        int commandId = 0;
        for(int i = 0; i < COMMAND_ID_SIZE; i++) {
            commandId |= Byte.toUnsignedInt(data[i]) << (8 * i);
        }

        BiFunction<Level, String[], String> command = this.commands.get(commandId);

        String responseMsg = command != null ? command.apply(level, args) : "This command id does not exist";

        try {
            clientChannel.write(ByteBuffer.wrap(responseMsg.getBytes(ENCODING)));
        }
        catch(IOException e) {
            TCPControllerMod2.LOGGER.info("Could not write back to client");
        }
    }

    private void closeConnection(SelectionKey key, Level level) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        InetSocketAddress address = (InetSocketAddress) clientChannel.getRemoteAddress();

        this.clientMap.remove(clientChannel);

        this.broadcastMessage(level, "Client %s:%d disconnected.".formatted(address.getHostString(), address.getPort()));

        key.cancel();
        clientChannel.close();
    }

    private void broadcastMessage(Level level, String message) {
        Objects.requireNonNull(level.getServer()).getPlayerList()
                .broadcastMessage(new TextComponent(message), ChatType.SYSTEM, UUID.randomUUID());
    }

    private static class SocketDataInfo {
        public byte[] data = new byte[0];
        public int currentIndex = 0;

        public void appendData(byte[] source, int from, int size) {
            System.arraycopy(source, from, this.data, this.currentIndex, size);
            this.currentIndex += size;
        }

        public void extendData(int amount) {
            byte[] newData = new byte[this.data.length + amount];

            System.arraycopy(this.data, 0, newData, 0, Math.min(this.data.length, newData.length));

            this.data = newData;
            this.currentIndex = Math.min(this.currentIndex, this.data.length);
        }

        public void reset() {
            this.data = new byte[0];
            this.currentIndex = 0;
        }
    }
}
