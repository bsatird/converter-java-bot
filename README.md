# 🪙 Inteligentny Konwerter Walut (Telegram Bot)

Ten projekt to zaawansowany bot na platformę Telegram, napisany w języku **Java**. Umożliwia on konwersję tradycyjnych walut fiat (poprzez API Narodowego Banku Polskiego) oraz kryptowalut (poprzez Binance API) w czasie rzeczywistym. Bazową walutą jest Polski Złoty (PLN).

* **Nazwa bota w Telegramie:** `@sudelimasbot`

---

## ✨ Główne Możliwości

* **Konwersja na żywo:** Szybkie przeliczanie popularnych walut (`USD`, `EUR`, `PLN`, `UAH`, `GBP`) oraz kryptowalut (`BTC`, `ETH`, `USDT`).
* **Wykresy i Historia (30 dni):** Bot automatycznie generuje i wysyła graficzne wykresy z historią kursów walut.
* **Powiadomienia i Alerty:** Ustawianie własnych progów cenowych z automatyczną weryfikacją kursu co godzinę.
* **Osobiście Portfel (Mój Portfel):** Zarządzanie wirtualnym portfelem z automatycznym przeliczaniem sumy wszystkich aktywów na PLN.

---

## 🎯 OOP & Architecture Highlights

* **Custom Exception Handling:** Wykorzystanie autorskich wyjątków (`try-catch`) do niezawodnej obsługi błędów sieciowych oraz połączeń z bazą danych.
* **Advanced Data Modeling:** Zastosowanie zasad OOP (dziedziczenie, interfejsy, klasy abstrakcyjne) oraz kolekcji (`ArrayList`, `HashMap`).
* **Smart API Caching:** Redukcja obciążenia sieciowego i ochrona przed limitami API dzięki buforowaniu kursów walut w bazie PostgreSQL (1h cache).

---

## 🛠 Wymagania przed uruchomieniem

1. **Java Development Kit (JDK):** Wersja 11, 17 lub wyższa.
2. **PostgreSQL Database:** Zainstalowana i uruchomiona baza danych.
3. **Maven:** System zarządzania zależnościami w projekcie.

---

## 🚀 Jak uruchomić lokalnie (How to Run)

### 1. Klonowanie repozytorium:
```bash
git clone [https://github.com/bsatird/converter-java-bot.git](https://github.com/bsatird/converter-java-bot.git)
cd converter-java-bot
