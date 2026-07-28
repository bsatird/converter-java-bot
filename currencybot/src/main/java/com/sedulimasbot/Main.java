package com.sedulimasbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MyCurrencyBot bot = new MyCurrencyBot();
            botsApi.registerBot(bot);
            
            System.out.println("Bot został pomyślnie uruchomiony i jest gotowy do pracy!");
        } catch (Exception e) {
            System.err.println("Błąd podczas uruchamiania bota: " + e.getMessage());
            e.printStackTrace();
        }
    }
}