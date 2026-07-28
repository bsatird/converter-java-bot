package com.sedulimasbot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyCurrencyBot extends TelegramLongPollingBot {
    private final CurrencyService currencyService;
    private final DatabaseManager dbManager;

    private static class UserSession {
        enum State { NONE, WAITING_FOR_AMOUNT, WAITING_FOR_ALERT_RATE, WAITING_FOR_PORTFOLIO_ADD, WAITING_FOR_PORTFOLIO_SUB }
        State state = State.NONE;
        String tempCurrency = null;
        double tempAmount = 0.0;
    }
    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    private final String[] POPULAR_CURRENCIES = {"USD", "EUR", "PLN", "GBP", "UAH", "BTC", "ETH"};
    private final Pattern SMART_CONVERT_PATTERN = Pattern.compile("^(\\d+[.,]?\\d*)\\s+([a-zA-Z]{3,4})\\s+(?:w|in|na\\s+)?([a-zA-Z]{3,4})$", Pattern.CASE_INSENSITIVE);

    public MyCurrencyBot() {
        this.dbManager = new DatabaseManager();
        this.currencyService = new CurrencyService(dbManager);
        
        AlertService alertService = new AlertService(dbManager, currencyService, message -> {
            try { execute(message); } catch (Exception e) { e.printStackTrace(); }
        });
        alertService.start();
    }

    @Override
    public String getBotUsername() { return "MySmartCurrencyConverterBot"; }

    @Override
    public String getBotToken() { return "YOUR_TELEGRAM_BOT_TOKEN"; }

    private UserSession getSession(long chatId) {
        return sessions.computeIfAbsent(chatId, k -> new UserSession());
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            UserSession session = getSession(chatId);

            if (text.equals("💱 Konwertuj") || text.equals("📈 Historia") || text.equals("💼 Mój Portfel") ||
                text.equals("🔔 Powiadomienia") || text.equals("ℹ️ Pomoc") || text.startsWith("/start")) {
                session.state = UserSession.State.NONE;
            }

            Matcher matcher = SMART_CONVERT_PATTERN.matcher(text);
            if (matcher.matches() && session.state == UserSession.State.NONE) {
                double amount = Double.parseDouble(matcher.group(1).replace(",", "."));
                String fromCurrency = matcher.group(2).toUpperCase();
                String toCurrency = matcher.group(3).toUpperCase();
                
                SendMessage waitMsg = new SendMessage(String.valueOf(chatId), "⏳ Przeliczam...");
                try {
                    int msgId = execute(waitMsg).getMessageId();
                    performConversion(chatId, msgId, amount, fromCurrency, toCurrency);
                } catch (Exception e) {}
                return;
            }

            if (session.state == UserSession.State.WAITING_FOR_AMOUNT) {
                try {
                    double amount = Double.parseDouble(text.replace(",", "."));
                    session.tempAmount = amount;
                    session.state = UserSession.State.NONE;
                    sendInlineCurrencies(chatId, "Wybrałeś kwotę " + amount + ". Z jakiej waluty chcesz przeliczyć?", "from_" + amount + "_");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ To nie wygląda na liczbę. Spróbuj wpisać poprawną kwotę (np. 150.50):");
                }
                return;
            }

            if (session.state == UserSession.State.WAITING_FOR_ALERT_RATE) {
                try {
                    double rate = Double.parseDouble(text.replace(",", "."));
                    dbManager.addAlert(chatId, session.tempCurrency, rate);
                    session.state = UserSession.State.NONE;
                    sendMessage(chatId, String.format("🔔 Powiadomienie utworzone! Napiszę do Ciebie, gdy tylko kurs %s spadnie poniżej %.4f PLN.", session.tempCurrency, rate));
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Nieprawidłowy format. Wpisz poprawny kurs w PLN (np. 4.15):");
                }
                return;
            }

            if (session.state == UserSession.State.WAITING_FOR_PORTFOLIO_ADD) {
                try {
                    String[] parts = text.split(" ");
                    if (parts.length != 2) throw new Exception();
                    double amount = Double.parseDouble(parts[0].replace(",", "."));
                    String currency = parts[1].toUpperCase();
                    
                    dbManager.addToPortfolio(chatId, currency, amount);
                    session.state = UserSession.State.NONE;
                    sendMessage(chatId, "✅ Pomyślnie dodano " + amount + " " + currency + " do Twojego portfela!");
                    showPortfolio(chatId); 
                } catch (Exception e) {
                    sendMessage(chatId, "❌ Nieprawidłowy format. Wpisz kwotę i walutę oddzieloną spacją (np. `100 USD`):");
                }
                return;
            }

            // НОВА ЛОГІКА: Очікування введення суми для ВІДНІМАННЯ з портфеля
            if (session.state == UserSession.State.WAITING_FOR_PORTFOLIO_SUB) {
                try {
                    String[] parts = text.split(" ");
                    if (parts.length != 2) throw new Exception();
                    double amount = Double.parseDouble(parts[0].replace(",", "."));
                    String currency = parts[1].toUpperCase();
                    
                    dbManager.subtractFromPortfolio(chatId, currency, amount);
                    session.state = UserSession.State.NONE;
                    sendMessage(chatId, "✅ Pomyślnie odjęto " + amount + " " + currency + " z Twojego portfela!");
                    showPortfolio(chatId); 
                } catch (Exception e) {
                    sendMessage(chatId, "❌ Nieprawidłowy format. Wpisz kwotę i walutę oddzieloną spacją (np. `50 USD`):");
                }
                return;
            }

            if (text.startsWith("/start")) {
                sendMenuMessage(chatId, "Cześć! Jestem inteligentnym konwerterem walut.\nWybierz opcję z menu poniżej 👇");
                return;
            }
            if (text.equals("💱 Konwertuj")) { sendAmountSelection(chatId); return; }
            if (text.equals("📈 Historia")) { sendInlineCurrencies(chatId, "Wybierz walutę, aby zobaczyć wykres (z 30 dni):", "hist_"); return; }
            if (text.equals("🔔 Powiadomienia")) { sendInlineCurrencies(chatId, "Wybierz walutę do śledzenia spadku (wobec PLN):", "alert_"); return; }
            if (text.equals("💼 Mój Portfel")) { showPortfolio(chatId); return; }
            if (text.equals("ℹ️ Pomoc")) {
                sendMessage(chatId, "Wszystkie akcje możesz wykonać za pomocą przycisków!\n\n💡 **Tip:** Możesz po prostu napisać np. `150 USD PLN` lub `0.5 BTC EUR`, aby błyskawicznie przeliczyć bez klikania.");
                return;
            }

            if (text.toLowerCase().startsWith("/convert")) { handleConvertCommand(chatId, text); return; }
            if (text.toLowerCase().startsWith("/history")) { handleHistoryCommand(chatId, text); return; }
            if (text.toLowerCase().startsWith("/alert")) { handleAlertCommand(chatId, text); return; }
            if (text.toLowerCase().startsWith("/p_add")) { handlePortfolioAdd(chatId, text); return; }
            if (text.toLowerCase().startsWith("/p_sub")) { handlePortfolioSub(chatId, text); return; } // Команда для віднімання
            if (text.toLowerCase().startsWith("/p_clear")) { 
                dbManager.clearPortfolio(chatId);
                sendMessage(chatId, "🗑️ Twój portfel został całkowicie wyczyszczony.");
                return; 
            }

            sendMessage(chatId, "Użyj przycisków z menu na dole ekranu lub napisz szybką komendę (np. `100 USD PLN`).");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        UserSession session = getSession(chatId);

        if (data.startsWith("amt_")) {
            String val = data.substring(4);
            if (val.equals("custom")) {
                session.state = UserSession.State.WAITING_FOR_AMOUNT;
                editMessageText(chatId, messageId, "Wpisz kwotę, którą chcesz przeliczyć (np. 125.50):");
            } else {
                double amount = Double.parseDouble(val);
                editMessageTextWithKeyboard(chatId, messageId, "Z jakiej waluty chcesz przeliczyć kwotę " + amount + "?", createCurrenciesKeyboard("from_" + amount + "_"));
            }
        }
        else if (data.startsWith("from_")) {
            String[] parts = data.split("_");
            String amount = parts[1];
            String fromCurrency = parts[2];
            editMessageTextWithKeyboard(chatId, messageId, "Kwota: " + amount + " " + fromCurrency + "\nNa jaką walutę chcesz przeliczyć?", createCurrenciesKeyboard("to_" + amount + "_" + fromCurrency + "_"));
        }
        else if (data.startsWith("to_")) {
            String[] parts = data.split("_");
            double amount = Double.parseDouble(parts[1]);
            String fromCurr = parts[2];
            String toCurr = parts[3];
            performConversion(chatId, messageId, amount, fromCurr, toCurr);
        }
        else if (data.startsWith("hist_")) {
            String curr = data.substring(5);
            if (curr.equals("PLN")) {
                editMessageText(chatId, messageId, "❌ Historia dla PLN nie jest dostępna, ponieważ jest to waluta bazowa.");
                return;
            }
            editMessageText(chatId, messageId, "⏳ Zbieram dane i rysuję wykres z ostatnich 30 dni dla " + curr + "...");
            generateHistory(chatId, curr);
        }
        else if (data.startsWith("alert_")) {
            String curr = data.substring(6);
            if (curr.equals("PLN")) {
                editMessageText(chatId, messageId, "❌ Nie można ustawić alertu na walutę bazową (PLN).");
                return;
            }
            session.tempCurrency = curr;
            session.state = UserSession.State.WAITING_FOR_ALERT_RATE;
            editMessageText(chatId, messageId, "Wybrałeś " + curr + ".\nWpisz docelowy kurs w PLN (np. 4.15). Gdy kurs spadnie poniżej tej wartości, wyślę Ci powiadomienie:");
        }
        else if (data.equals("port_add")) {
            session.state = UserSession.State.WAITING_FOR_PORTFOLIO_ADD;
            editMessageText(chatId, messageId, "Wpisz kwotę i walutę oddzieloną spacją, którą chcesz **DODAĆ** do portfela.\nPrzykłady:\n`150 USD`\n`500 EUR`");
        }
        else if (data.equals("port_sub")) {
            session.state = UserSession.State.WAITING_FOR_PORTFOLIO_SUB;
            editMessageText(chatId, messageId, "Wpisz kwotę i walutę oddzieloną spacją, którą chcesz **ODJĄĆ** z portfela.\nPrzykłady:\n`50 USD`\n`10 PLN`");
        }
        else if (data.equals("port_clear")) {
            dbManager.clearPortfolio(chatId);
            editMessageText(chatId, messageId, "🗑️ Twój portfel został całkowicie wyczyszczony.");
        }
    }

    private void performConversion(long chatId, int messageId, double amount, String fromCurrency, String toCurrency) {
        try {
            double rateFrom = currencyService.getRate(fromCurrency);
            double rateTo = currencyService.getRate(toCurrency);

            double amountInBase = amount * rateFrom; 
            double result = amountInBase / rateTo; 

            String response = String.format("💵 %.4f %s = **%.4f %s**\n\n_(Kurs %s: %.4f PLN | Kurs %s: %.4f PLN)_", 
                    amount, fromCurrency, result, toCurrency, 
                    fromCurrency, rateFrom, toCurrency, rateTo);
            
            editMessageText(chatId, messageId, response);
        } catch (Exception e) {
            editMessageText(chatId, messageId, "❌ Błąd: Nieprawidłowa waluta lub problem z API NBP/Binance.");
        }
    }

    private void showPortfolio(long chatId) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "⏳ Analizuję Twój portfel...");
        try {
            int msgId = execute(msg).getMessageId();
            
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Double> portfolio = dbManager.getPortfolio(chatId);
                    if (portfolio.isEmpty()) {
                        editMessageTextWithKeyboard(chatId, msgId, "💼 **Twój Portfel jest pusty.**\nKliknij poniżej, aby dodać środki.", createPortfolioKeyboard());
                        return;
                    }

                    StringBuilder sb = new StringBuilder("💼 **Mój Portfel:**\n\n");
                    double totalPln = 0;

                    for (Map.Entry<String, Double> entry : portfolio.entrySet()) {
                        String currency = entry.getKey();
                        double amount = entry.getValue();
                        double rate = currencyService.getRate(currency);
                        double valueInPln = amount * rate;
                        totalPln += valueInPln;

                        sb.append(String.format("🔹 %.4f %s (≈ %.2f PLN)\n", amount, currency, valueInPln));
                    }

                    sb.append("\n==================\n");
                    sb.append(String.format("💰 **Suma całkowita:** **%.2f PLN**", totalPln));

                    editMessageTextWithKeyboard(chatId, msgId, sb.toString(), createPortfolioKeyboard());
                } catch (Exception e) {
                    editMessageText(chatId, msgId, "❌ Błąd podczas obliczania wartości portfela.");
                }
            });
        } catch (Exception e) {}
    }

    private InlineKeyboardMarkup createPortfolioKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton btnAdd = new InlineKeyboardButton("➕ Dodaj");
        btnAdd.setCallbackData("port_add");
        
        InlineKeyboardButton btnSub = new InlineKeyboardButton("➖ Odejmij");
        btnSub.setCallbackData("port_sub");

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton btnClear = new InlineKeyboardButton("🗑️ Wyczyść wszystko");
        btnClear.setCallbackData("port_clear");
        
        row1.add(btnAdd);
        row1.add(btnSub);
        row2.add(btnClear);
        
        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);
        return markup;
    }

    // Текстова команда для віднімання
    private void handlePortfolioSub(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendMessage(chatId, "❌ Format komendy: `/p_sub [kwota] [waluta]`\nPrzykład: `/p_sub 50 USD`");
            return;
        }
        try {
            double amount = Double.parseDouble(parts[1].replace(",", "."));
            String currency = parts[2].toUpperCase();

            dbManager.subtractFromPortfolio(chatId, currency, amount);
            sendMessage(chatId, String.format("✅ Odjęto %.4f %s z Twojego portfela!", amount, currency));
            showPortfolio(chatId);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Błąd! Sprawdź poprawność wpisanych danych.");
        }
    }

    private void handlePortfolioAdd(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendMessage(chatId, "❌ Format komendy: `/p_add [kwota] [waluta]`\nPrzykład: `/p_add 100 USD`");
            return;
        }

        try {
            double amount = Double.parseDouble(parts[1].replace(",", "."));
            String currency = parts[2].toUpperCase();

            currencyService.getRate(currency); // Перевірка валюти

            dbManager.addToPortfolio(chatId, currency, amount);
            sendMessage(chatId, String.format("✅ Dodano %.4f %s do Twojego portfela!", amount, currency));
            showPortfolio(chatId);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Nieprawidłowa waluta lub problem з API.");
        }
    }

    private void generateHistory(long chatId, String currency) {
        CompletableFuture.runAsync(() -> {
            try {
                List<Double> rates = currencyService.getHistoryRates(currency, 30);
                String chartUrl = ChartGenerator.getChartUrl(currency, rates);

                if (chartUrl == null) {
                    sendMessage(chatId, "❌ Błąd podczas generowania wykresu.");
                    return;
                }

                double current = rates.get(rates.size() - 1);
                double previous = rates.get(0);
                double change = current - previous;
                double changePercent = (previous != 0) ? (change / previous) * 100 : 0;
                String trendIcon = change > 0 ? "📈 Rosnący" : (change < 0 ? "📉 Malejący" : "➡️ Stabilny");

                String caption = String.format(
                    "📈 **Analityka: %s do PLN (ostatnie 30 dni)**\n\n" +
                    "Trend: %s\n" +
                    "🔹 Aktualnie: **%.4f PLN**\n" +
                    "🔺 Zmiana: %+.4f PLN (%+.2f%%)",
                    currency, trendIcon, current, change, changePercent
                );

                SendPhoto photo = new SendPhoto();
                photo.setChatId(String.valueOf(chatId));
                photo.setPhoto(new InputFile(chartUrl));
                photo.setCaption(caption);
                photo.setParseMode("Markdown");
                
                execute(photo); 
            } catch (Exception e) {
                sendMessage(chatId, "❌ Błąd podczas pobierania historii z NBP.");
            }
        });
    }

    private void handleConvertCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendMessage(chatId, "❌ Format komendy: `/convert [kwota] [waluta_1] [waluta_2]`");
            return;
        }

        try {
            double amount = Double.parseDouble(parts[1].replace(",", "."));
            String fromCurrency = parts[2].toUpperCase();
            String toCurrency = (parts.length > 3) ? parts[3].toUpperCase() : "PLN";

            double rateFrom = currencyService.getRate(fromCurrency);
            double rateTo = currencyService.getRate(toCurrency);

            double amountInPln = amount * rateFrom;
            double result = amountInPln / rateTo;

            String response = String.format("💵 %.2f %s = **%.2f %s**", amount, fromCurrency, result, toCurrency);
            sendMessage(chatId, response);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Błąd! Sprawdź poprawność walut.");
        }
    }

    private void handleHistoryCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) return;
        generateHistory(chatId, parts[1].toUpperCase());
    }

    private void handleAlertCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) return;
        try {
            String currency = parts[1].toUpperCase();
            double targetRate = Double.parseDouble(parts[2].replace(",", "."));
            dbManager.addAlert(chatId, currency, targetRate);
            sendMessage(chatId, "🔔 Powiadomienie utworzone!");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Błąd formatu.");
        }
    }

    private void sendAmountSelection(long chatId) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "Wybierz kwotę do przeliczenia:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineBtn("20", "amt_20"));
        row1.add(createInlineBtn("100", "amt_100"));
        row1.add(createInlineBtn("500", "amt_500"));
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineBtn("✍️ Własna kwota", "amt_custom"));
        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        try { execute(msg); } catch (Exception e) {}
    }

    private void sendInlineCurrencies(long chatId, String text, String callbackPrefix) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.setReplyMarkup(createCurrenciesKeyboard(callbackPrefix));
        try { execute(msg); } catch (Exception e) {}
    }

    private InlineKeyboardMarkup createCurrenciesKeyboard(String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        for (int i = 0; i < POPULAR_CURRENCIES.length; i++) {
            currentRow.add(createInlineBtn(POPULAR_CURRENCIES[i], prefix + POPULAR_CURRENCIES[i]));
            if (currentRow.size() == 3 || i == POPULAR_CURRENCIES.length - 1) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createInlineBtn(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setParseMode("Markdown"); 
        try { execute(message); } catch (Exception e) {}
    }

    private void editMessageText(long chatId, int messageId, String text) {
        EditMessageText msg = new EditMessageText();
        msg.setChatId(String.valueOf(chatId));
        msg.setMessageId(messageId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        try { execute(msg); } catch (Exception e) {}
    }

    private void editMessageTextWithKeyboard(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText msg = new EditMessageText();
        msg.setChatId(String.valueOf(chatId));
        msg.setMessageId(messageId);
        msg.setText(text);
        msg.setReplyMarkup(markup);
        msg.setParseMode("Markdown");
        try { execute(msg); } catch (Exception e) {}
    }

    private void sendMenuMessage(long chatId, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setParseMode("Markdown"); 
        
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("💱 Konwertuj"));
        row1.add(new KeyboardButton("📈 Historia"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("💼 Mój Portfel"));
        row2.add(new KeyboardButton("🔔 Powiadomienia"));
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("ℹ️ Pomoc"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard); 
        message.setReplyMarkup(keyboardMarkup);

        try { execute(message); } catch (Exception e) {}
    }
}