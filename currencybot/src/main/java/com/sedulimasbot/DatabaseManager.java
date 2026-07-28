package com.sedulimasbot;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final String URL = System.getenv().getOrDefault("DB_URL", "YOUR_DATABASE_URL");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "YOUR_DATABASE_USER");
    private static final String PASS = System.getenv().getOrDefault("DB_PASS", "YOUR_DATABASE_PASSWORD");
    
    private static final long CACHE_DURATION_MS = 3600000; 

    public DatabaseManager() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS rates (" +
                    "currency VARCHAR(10) PRIMARY KEY, " +
                    "rate DOUBLE PRECISION NOT NULL, " +
                    "updated_at BIGINT NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS alerts (" +
                    "id SERIAL PRIMARY KEY, " +
                    "chat_id BIGINT NOT NULL, " +
                    "currency VARCHAR(10) NOT NULL, " +
                    "target_rate DOUBLE PRECISION NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS portfolio (" +
                    "chat_id BIGINT NOT NULL, " +
                    "currency VARCHAR(10) NOT NULL, " +
                    "amount DOUBLE PRECISION NOT NULL, " +
                    "PRIMARY KEY (chat_id, currency))");
                    
            System.out.println("Połączenie z PostgreSQL zakończone sukcesem. Tabele gotowe.");
        } catch (SQLException e) {
            System.err.println("Błąd inicjalizacji bazy danych!");
            e.printStackTrace();
        }
    }

    public Double getCachedRate(String currency) {
        String query = "SELECT rate, updated_at FROM rates WHERE currency = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, currency.toUpperCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                if (System.currentTimeMillis() - rs.getLong("updated_at") < CACHE_DURATION_MS) {
                    return rs.getDouble("rate"); 
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null; 
    }

    public void saveRate(String currency, double rate) {
        String query = "INSERT INTO rates (currency, rate, updated_at) VALUES (?, ?, ?) " +
                       "ON CONFLICT (currency) DO UPDATE SET rate = EXCLUDED.rate, updated_at = EXCLUDED.updated_at";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, currency.toUpperCase());
            pstmt.setDouble(2, rate);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void addAlert(long chatId, String currency, double targetRate) {
        String query = "INSERT INTO alerts (chat_id, currency, target_rate) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, currency.toUpperCase());
            pstmt.setDouble(3, targetRate);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<Alert> getActiveAlerts() {
        List<Alert> alerts = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, chat_id, currency, target_rate FROM alerts")) {
            while (rs.next()) {
                alerts.add(new Alert(rs.getInt("id"), rs.getLong("chat_id"), rs.getString("currency"), rs.getDouble("target_rate")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return alerts;
    }

    public void deleteAlert(int alertId) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM alerts WHERE id = ?")) {
            pstmt.setInt(1, alertId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- МЕТОДИ ДЛЯ ПОРТФЕЛЯ ---
    public void addToPortfolio(long chatId, String currency, double amount) {
        String query = "INSERT INTO portfolio (chat_id, currency, amount) VALUES (?, ?, ?) " +
                       "ON CONFLICT (chat_id, currency) DO UPDATE SET amount = portfolio.amount + EXCLUDED.amount";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, currency.toUpperCase());
            pstmt.setDouble(3, amount);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // НОВИЙ МЕТОД: Віднімання з портфеля
    public void subtractFromPortfolio(long chatId, String currency, double amount) {
        String checkQuery = "SELECT amount FROM portfolio WHERE chat_id = ? AND currency = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            
            checkStmt.setLong(1, chatId);
            checkStmt.setString(2, currency.toUpperCase());
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                double currentAmount = rs.getDouble("amount");
                if (currentAmount <= amount) {
                    // Якщо віднімаємо все або більше ніж є — повністю видаляємо запис
                    try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM portfolio WHERE chat_id = ? AND currency = ?")) {
                        deleteStmt.setLong(1, chatId);
                        deleteStmt.setString(2, currency.toUpperCase());
                        deleteStmt.executeUpdate();
                    }
                } else {
                    // Якщо віднімаємо частину — оновлюємо баланс
                    try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE portfolio SET amount = amount - ? WHERE chat_id = ? AND currency = ?")) {
                        updateStmt.setDouble(1, amount);
                        updateStmt.setLong(2, chatId);
                        updateStmt.setString(3, currency.toUpperCase());
                        updateStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Map<String, Double> getPortfolio(long chatId) {
        Map<String, Double> portfolio = new HashMap<>();
        String query = "SELECT currency, amount FROM portfolio WHERE chat_id = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                portfolio.put(rs.getString("currency"), rs.getDouble("amount"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return portfolio;
    }

    public void clearPortfolio(long chatId) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM portfolio WHERE chat_id = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}