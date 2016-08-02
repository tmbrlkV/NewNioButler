package com.butler.util.json;

import com.butler.util.entity.User;

public class JsonObject {
    private String command;
    private User user;

    public JsonObject() {
    }

    JsonObject(String command, User user) {
        this.command = command;
        this.user = user;
    }

    public String getCommand() {
        return command;
    }

    public User getUser() {
        return user;
    }
}
