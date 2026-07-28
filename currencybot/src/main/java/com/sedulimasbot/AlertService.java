package com.sedulimasbot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AlertService {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final DatabaseManager dbManager;
    private final CurrencyService currencyService;
    private final Consumer<SendMessage> messageSender;

    public AlertService(DatabaseManager dbManager, CurrencyService currencyService, Consumer<SendMessage> messageSender) {
        this.dbManager = dbManager;
        this.currencyService = currencyService;
        this.messageSender = messageSender;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAlerts, 0, 60, TimeUnit.MINUTES);
        System.out.println("Usługa powiadomień w tle została pomyślnie uruchomiona.");
    }

    private void checkAlerts() {
        System.out.println("[AlertService] Rozpoczęcie sprawdzania kursów...");
        List<Alert> activeAlerts = dbManager.getActiveAlerts();

        if (activeAlerts.isEmpty()) {
            return;
        }

        for (Alert alert : activeAlerts) {
            try {
                double currentRate = currencyService.getRate(alert.getCurrency());

                if (currentRate <= alert.getTargetRate()) {
                    String msgText = String.format("🚨 **POWIADOMIENIE!**\n\nWaluta: %s\nAktualny kurs: **%.4f PLN**\n(Twój cel to: %.4f)", 
                            alert.getCurrency(), currentRate, alert.getTargetRate());

                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(alert.getChatId()));
                    message.setText(msgText);
                    message.setParseMode("Markdown");

                    messageSender.accept(message);
                    dbManager.deleteAlert(alert.getId());
                }
            } catch (Exception e) {
                System.err.println("Błąd podczas przetwarzania powiadomienia ID " + alert.getId() + ": " + e.getMessage());
            }
        }
    }
}