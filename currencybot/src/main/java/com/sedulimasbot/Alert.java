package com.sedulimasbot;

public class Alert {
    private final int id;
    private final long chatId;
    private final String currency;
    private final double targetRate;

    public Alert(int id, long chatId, String currency, double targetRate) {
        this.id = id;
        this.chatId = chatId;
        this.currency = currency;
        this.targetRate = targetRate;
    }

    public int getId() { return id; }
    public long getChatId() { return chatId; }
    public String getCurrency() { return currency; }
    public double getTargetRate() { return targetRate; }
}