package com.reportsystem.common.replay.actions;

public class ChatAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final String message;
    private final boolean isCommand;

    public ChatAction(String message, boolean isCommand) {
        super();
        this.message = message;
        this.isCommand = isCommand;
    }

    public String getMessage() {
        return message;
    }

    public boolean isCommand() {
        return isCommand;
    }
}