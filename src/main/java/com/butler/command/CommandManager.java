package com.butler.command;

import com.butler.server.ServerDataEvent;
import com.butler.socket.DatabaseSocketHandler;
import com.butler.socket.SenderSocketHandler;
import com.butler.util.entity.User;
import com.butler.util.json.JsonMessage;
import com.butler.util.json.JsonObject;
import com.butler.util.json.JsonObjectFactory;
import org.zeromq.ZMQ;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager {
    private ZMQ.Context context = ZMQ.context(1);
    private SenderSocketHandler sender = new SenderSocketHandler(context);

    private Map<String, Command> commandMap = new ConcurrentHashMap<String, Command>() {{
        Command databaseCommand = request -> {
            try (DatabaseSocketHandler handler = new DatabaseSocketHandler(context)) {
                handler.send(request);
                String reply = handler.receive();
                User user = JsonObjectFactory.getObjectFromJson(reply, User.class);
                return JsonObjectFactory.getJsonString(Optional.ofNullable(user).orElse(new User()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        };
        put(Command.GET_USER_BY_LOGIN_PASSWORD, databaseCommand);
        put(Command.GET_USER_BY_LOGIN, databaseCommand);
        put(Command.NEW_USER, databaseCommand);
        put(Command.MESSAGE, request -> {
            sender.send(request);
            return request;
        });
    }};

    public String execute(ServerDataEvent dataEvent) {
        String json = new String(dataEvent.getData());
        JsonObject databaseRequest = JsonObjectFactory.getObjectFromJson(json, JsonObject.class);
        JsonMessage message = JsonObjectFactory.getObjectFromJson(json, JsonMessage.class);

        Optional<JsonMessage> messageOptional = Optional.ofNullable(message);
        Optional<JsonObject> databaseRequestOptional = Optional.ofNullable(databaseRequest);

        String stringCommand = databaseRequestOptional.map(JsonObject::getCommand)
                .orElseGet(() -> messageOptional.map(JsonMessage::getCommand).orElse(Command.NO_COMMAND));

        Command command = commandMap.getOrDefault(stringCommand, request -> Command.NO_COMMAND);
        String jsonString = databaseRequestOptional.map(JsonObjectFactory::getJsonString)
                .orElseGet(() -> messageOptional.map(JsonObjectFactory::getJsonString).orElse(Command.NO_COMMAND));
        return command.execute(jsonString);
    }
}
