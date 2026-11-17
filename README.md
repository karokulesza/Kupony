# Coupon Service

Mikroserwis REST do kuponów rabatowych: tworzenie kuponu i jego użycie, z limitem użyć odpornym na współbieżność,
kodami bez rozróżniania wielkości liter i ograniczeniem kraju po IP (z wykrywaniem VPN/proxy/Tor).

Wymagania: Java 25, Docker.

## Uruchomienie

```bash
docker compose up --build -d          # PostgreSQL + aplikacja na http://localhost:8080
docker compose down                   # zatrzymanie
```

```bash
curl -X POST localhost:8080/api/v1/coupons -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","country":"PL","maxUses":100}'
curl -X POST localhost:8080/api/v1/coupons/wiosna/redemptions -H 'Content-Type: application/json' \
  -d '{"userId":"3f2a9c1e-0000-4000-8000-000000000001","ip":"212.77.98.9"}'
```

## Stress test

```bash
SPRING_PROFILES_ACTIVE=stress docker compose up --build -d
java stress/StressTest.java --coupons=5000 --min-uses=2000 --max-uses=10000 --threads=16
```

Parametry: `java stress/StressTest.java --help`. Profil `stress` zastępuje ip-api.com stałym krajem (PL), bo darmowy
plan pozwala na 45 zapytań na minutę. Test sprawdza, że każdy kupon użyto dokładnie `maxUses` razy, że kolejne użycie
dostaje `COUPON_EXHAUSTED` i że wszystkie kupony trafiły do archiwum.

## Testy i jakość

```bash
./mvnw verify          # testy jednostkowe + integracyjne (Testcontainers) + JaCoCo: linie >= 90%, gałęzie >= 85%
./sonar/analyze.sh     # jednorazowy SonarQube na czas analizy; build pada, gdy bramka jakości nie przejdzie
```

## Decyzje architektoniczne

- **Architektura heksagonalna.** `domain` to czysta Java (model i porty), `application` to przypadki użycia,
  adaptery `in.web` (REST), `out.persistence` (Spring Data JPA) i `out.geo` (ip-api.com).
- **Aktywne i archiwalne dane w osobnych tabelach.** Kupony i użycia, które jeszcze się liczą, leżą w
  `active_coupons` / `coupon_redemptions`. Wyczerpane trafiają do `used_coupons` / `archived_redemptions`. Gorące
  tabele i ich indeksy zostają małe, więc wyszukanie kuponu, blokada licznika i sprawdzenie „ten użytkownik już użył”
  działają na niewielkim zbiorze, niezależnie od tego, ile historii się uzbiera. Powód odmowy zależy tylko od stanu
  kuponu, nie od tego, w której tabeli akurat leży.
- **Licznik przyjazny dla PostgreSQL.** Żaden indeks nie obejmuje `used_count`, a tabela ma `fillfactor 90`, więc
  aktualizacja licznika to HOT update bez przepisywania indeksów.
- **Limit użyć.** Jeden atomowy, warunkowy `UPDATE … WHERE exhausted = false` na READ COMMITTED, bez schematu
  „odczytaj, policz, zapisz”. Baza po commicie konkurencyjnej transakcji sprawdza warunek ponownie, więc limitu nie da
  się przekroczyć.
- **Unikalny kod tylko wśród aktywnych kuponów.** Kolumna `active_code` z `UNIQUE` dostaje `NULL` po wyczerpaniu
  kuponu, więc ten sam kod można użyć ponownie dla nowego kuponu. Bez indeksów częściowych specyficznych dla bazy.
- **Kod bez wielkości liter.** Znormalizowany w Javie (`toLowerCase(Locale.ROOT)`), a nie funkcją bazy.
- **Jeden użytkownik, jedno użycie.** `UNIQUE (coupon_id, user_id)` na tabeli użyć.
- **Geolokalizacja przed transakcją.** Wywołanie ip-api nigdy nie trzyma blokady w bazie. Gdy dostawca nie działa
  albo jest limit, żądanie jest odrzucane (fail closed, `503` z `Retry-After`). Wyniki trafiają do własnego cache LRU
  z TTL, bez dodatkowej biblioteki.
- **Archiwizacja oszczędna dla procesora i bazy.** Przenosi jeden wątek w tle, nigdy wątek żądania. Uruchamia go
  zdarzenie po commicie ostatniego użycia. Zadanie zapisane w tej samej transakcji (`archival_task`) gwarantuje, że
  nic nie zginie po awarii. Dane idą paczkami po 1000 wierszy, każda w krótkiej transakcji, z pauzą 50 ms między
  paczkami, żeby nie robić skoków I/O. Gdy nie ma pracy, poza jednym tanim zapytaniem na minutę nic nie odpytuje bazy.
  Dzierżawa i `SKIP LOCKED` sprawiają, że kilka instancji nie przeniesie tych samych danych.
- **Przenośny schemat.** Liquibase z typami przenośnymi, zero natywnego SQL w kodzie. Strojenie tylko pod PostgreSQL
  siedzi w osobnym changesecie z `dbms: postgresql`.
- **Błędy w formacie RFC 9457 (Problem Details)** z polem `errorCode`, np. `COUPON_EXHAUSTED`, `VPN_DETECTED`,
  `COUPON_COUNTRY_MISMATCH`, `GEOLOCATION_UNAVAILABLE`.
- **Minimum zależności.** Spring Boot (web, data-jpa, validation, actuator), Liquibase, Lombok, Testcontainers.
