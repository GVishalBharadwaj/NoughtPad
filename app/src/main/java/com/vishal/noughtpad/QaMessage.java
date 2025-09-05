package com.vishal.noughtpad;

public class QaMessage {
    public static final String SENT_BY_USER = "user";
    public static final String SENT_BY_AI = "ai";

    String message;
    String sentBy;

    public QaMessage(String message, String sentBy) {
        this.message = message;
        this.sentBy = sentBy;
    }
}