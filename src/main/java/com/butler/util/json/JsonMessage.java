package com.butler.util.json;

public class JsonMessage {
    private String command;
    private String username;
    private String content;

    public JsonMessage() {
    }

    public JsonMessage(String command, String username, String content) {
        this.command = command;
        this.username = username;
        this.content = content;
    }

    public String getCommand() {
        return command;
    }

    public String getContent() {
        return content;
    }

    public String getUsername() {
        return username;
    }
}
