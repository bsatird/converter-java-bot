# Inteligentny Konwerter Walut (Telegram Bot)

Ten projekt to zaawansowany bot na platformę Telegram, napisany w języku Java. Umożliwia on konwersję tradycyjnych walut fiat (poprzez API Narodowego Banku Polskiego) oraz kryptowalut (poprzez Binance API) w czasie rzeczywistym. Bazową walutą jest Polski Złoty (PLN).

Nazwa bota w Telegramie: `@sudelimasbot` 

## Główne Możliwości
*Konwersja na żywo: Szybkie przeliczanie popularnych walut (USD, EUR, PLN, UAH, GBP) oraz kryptowalut (BTC, ETH, USDT) za pomocą interaktywnych przycisków lub pisząc bezpośrednio w czacie (np. `100 USD PLN`).
*Wykresy i Historia (30 dni): Bot automatycznie generuje i wysyła graficzne wykresy z historią kursów walut względem PLN.
*Powiadomienia i Alerty: Możliwość ustawienia własnego progu cenowego (np. powiadom mnie, gdy USD spadnie poniżej 4.15 PLN). Bot automatycznie weryfikuje kursy co godzinę i wysyła wiadomość!
*Osobisty Portfel (Mój Portfel): Możesz dodawać i odejmować różne waluty i kryptowaluty do swojego wirtualnego portfela. Bot przeliczy sumę wszystkich Twoich aktywów na PLN na podstawie aktualnych kursów rynkowych.

## Wymagania przed uruchomieniem
Aby zainstalować i uruchomić kod na swoim komputerze, będziesz potrzebować:
1. Java Development Kit (JDK): Zainstalowana Java w wersji 11, 17 lub wyższej.
2. Maven / Gradle: System zarządzania zależnościami (np. do pobrania bibliotek TelegramBots, Gson, bazy PostgreSQL).
3. Konto Telegram: Aby znaleźć bota 


## Przykłady interakcji bez klikania (Smart Input)
Możesz pisać do bota skrótami bez używania menu:
- `150 EUR PLN` -> natychmiast przeliczy 150 euro na złote.
- `0.05 BTC USD` -> przeliczy 0.05 Bitcoina na dolary.
- `/p_add 100 USD` -> doda 100 dolarów do Twojego portfela.
- `/p_sub 50 USD` -> odejmie 50 dolarów z portfela.
- `/p_clear` -> usunie wszystkie zapisane aktywa.