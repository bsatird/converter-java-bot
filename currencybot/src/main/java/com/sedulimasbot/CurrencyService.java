package com.sedulimasbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CurrencyService {
    private final DatabaseManager db;
    private final HttpClient httpClient;
    private final List<String> CRYPTO_CURRENCIES = List.of("BTC", "ETH", "USDT");

    public CurrencyService(DatabaseManager db) {
        this.db = db;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public double getRate(String currency) throws Exception {
        currency = currency.toUpperCase();
        
        // БАЗОВА ВАЛЮТА - ПОЛЬСЬКИЙ ЗЛОТИЙ
        if (currency.equals("PLN")) {
            return 1.0;
        }

        Double cachedRate = db.getCachedRate(currency);
        if (cachedRate != null) {
            return cachedRate;
        }

        double rate;

        if (CRYPTO_CURRENCIES.contains(currency)) {
            // КРИПТОВАЛЮТИ (через Binance)
            if (currency.equals("USDT")) {
                rate = getRate("USD"); // USDT прирівнюємо до долара
            } else {
                String binanceUrl = "https://api.binance.com/api/v3/ticker/price?symbol=" + currency + "USDT";
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(binanceUrl)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                    double priceInUsd = jsonObject.get("price").getAsDouble();
                    // Множимо ціну в доларах на курс долара в PLN
                    rate = priceInUsd * getRate("USD");
                } else {
                    throw new RuntimeException("Błąd API Binance: " + response.statusCode());
                }
            }
        } else {
            // ФІАТНІ ВАЛЮТИ (через NBP - Польський Банк)
            String nbpUrl = "http://api.nbp.pl/api/exchangerates/rates/a/" + currency + "/?format=json";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(nbpUrl)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                rate = jsonObject.getAsJsonArray("rates").get(0).getAsJsonObject().get("mid").getAsDouble();
            } else {
                throw new IllegalArgumentException("Nie znaleziono kursu dla: " + currency);
            }
        }

        db.saveRate(currency, rate);
        return rate;
    }

    public List<Double> getHistoryRates(String currency, int days) throws Exception {
        currency = currency.toUpperCase();
        List<Double> historicalRates = new ArrayList<>();
        
        if (CRYPTO_CURRENCIES.contains(currency)) {
            String symbol = currency.equals("USDT") ? "USDCUSDT" : currency + "USDT";
            String url = "https://api.binance.com/api/v3/klines?symbol=" + symbol + "&interval=1d&limit=" + days;
            
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                double usdRate = getRate("USD"); // Поточний курс USD в PLN
                
                for (int i = 0; i < jsonArray.size(); i++) {
                    double closePriceInUsd = jsonArray.get(i).getAsJsonArray().get(4).getAsDouble();
                    historicalRates.add(closePriceInUsd * usdRate); // Переводимо в PLN
                }
                return historicalRates;
            } else {
                throw new IllegalArgumentException("Błąd API Binance History");
            }
        }

        // Історія NBP для фіату
        String url = "http://api.nbp.pl/api/exchangerates/rates/a/" + currency + "/last/" + days + "/?format=json";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray ratesArray = jsonObject.getAsJsonArray("rates");
            for (int i = 0; i < ratesArray.size(); i++) {
                historicalRates.add(ratesArray.get(i).getAsJsonObject().get("mid").getAsDouble());
            }
        } else {
            throw new IllegalArgumentException("Nie udało się pobrać danych z NBP dla " + currency);
        }
        
        return historicalRates;
    }
}