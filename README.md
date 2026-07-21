# Virtual Pokédex

A Java desktop application for browsing, comparing, and managing Pokémon teams — backed by a MySQL database of 1,000+ records with a connection pool, a custom prefix Trie for autocomplete, and non-blocking UI rendering via SwingWorker.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Swing](https://img.shields.io/badge/GUI-Java%20Swing-blueviolet?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql)
![HikariCP](https://img.shields.io/badge/HikariCP-5.1-red?style=flat-square)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)

---

## The Problem

Most Java Swing projects that touch a database make the same mistakes:

- **Frozen UI** — database queries run on the Event Dispatch Thread, blocking all rendering until the query completes
- **Connection churn** — a new TCP connection opens for every query, adding 40–80ms of overhead per request
- **Buried data structures** — autocomplete logic lives as a private inner class inside a 600-line UI file, invisible to anyone reading the code
- **Text-dump analytics** — "team stats" means a raw `JOptionPane` string, not an actual visual

This project fixes all four.

---

## Architecture

```
┌─────────────────────────────────────────────┐
│              User Input (Search)             │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│           PokemonTrie (in-memory)           │
│   O(M) prefix lookup → suggestion list      │
└──────────────────────┬──────────────────────┘
                       │ on commit
                       ▼
┌─────────────────────────────────────────────┐
│              SwingWorker Thread             │
│   Runs DB query off the Event              │
│   Dispatch Thread → publishes result       │
│   back via done() on EDT                   │
└────────┬─────────────────────┬─────────────┘
         │                     │
         ▼                     ▼
┌─────────────────┐   ┌──────────────────────┐
│  HikariCP Pool  │   │  PokedexUI           │
│  (MySQL 3308)   │   │  renders card /      │
│  reuses conns   │   │  dashboard / arena   │
└────────┬────────┘   └──────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│              MySQL (Docker)                 │
│  - pokemon       (1,000+ rows, indexed)     │
│  - teams / team_members                     │
│  - evolution                                │
└─────────────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. PokemonTrie as a Standalone Class

The Trie was originally a private inner class buried at line 271 of `PokedexUI.java`. It is now a top-level file with full Javadoc, an explicit O(M) complexity annotation on both `insert()` and `getSuggestions()`, and a named constant for `MAX_SUGGESTIONS`. Autocomplete suggestions are served entirely from memory — no database query fires until the user selects a result.

### 2. HikariCP Connection Pool

The original implementation opened a new `DriverManager.getConnection()` on every query. Under rapid search interactions this created 40–80ms of TCP handshake overhead per call. HikariCP maintains a warm pool of reusable connections. Every `DatabaseManager` method documents which index it uses and why.

### 3. SwingWorker Everywhere

Java Swing has one rendering thread — the Event Dispatch Thread. Blocking it with a database call freezes the entire window. Every query in this app (startup, search, navigation, dashboard load) runs inside a `SwingWorker`, with results published back to the EDT via `done()`. The splash screen shows a live `JProgressBar` across three startup phases: pool warmup → name fetch → Trie build.

### 4. Lazy Pool Initialisation

The `HikariDataSource` is created on first use, not at class load. An eager static initialiser would throw `ExceptionInInitializerError` and kill the JVM silently if Docker was not running. The lazy approach catches the failure at the login step and surfaces it as a readable error dialog.

### 5. SQL Window Functions for Rankings

When a Pokémon card loads, an async query runs `RANK() OVER (PARTITION BY type_1 ORDER BY (hp + attack + defense + speed) DESC)` across the full dataset. The card subtitle shows the Pokémon's rank within its type group (e.g. *Grass / Poison · Rank #3 of 75*) without any application-side sorting.

### 6. ACID Transaction for Team Cloning

Duplicating a team requires two writes: `INSERT INTO teams` followed by a bulk `INSERT INTO team_members ... SELECT`. These run inside a single transaction (`setAutoCommit(false)` → `commit()` / `rollback()`). If the second write fails, the orphaned team row is rolled back — the database never ends up in a half-written state.

---

## Features

- **Search & Autocomplete** — Trie-backed suggestions appear as you type; the database is only queried on selection.
- **Team Builder** — Create named teams, add or remove Pokémon, clone an existing team atomically.
- **Versus Arena** — Side-by-side comparison of HP, Attack, Defense, Speed, and Base Stat Total.
- **Team Dashboard** — Visual stat bars for average power and type distribution across the roster.
- **Stat Leaderboards** — Top-10 rankings by any stat, queried live with `ORDER BY ... LIMIT 10`.
- **Evolution Chains** — Full evolution lines resolved through multi-table joins on the `evolution` table.
- **Dynamic Theming** — Card backgrounds change per Pokémon type with a gradient fallback for missing assets.

---

## Quick Start

### Prerequisites

- JDK 17 or higher
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- IntelliJ IDEA or any Maven-compatible IDE

### 1. Start the database

```bash
docker compose up -d
```

MySQL starts on `localhost:3308`. To stop it later: `docker stop pokedex_mysql`

### 2. Run the app

Open the project in IntelliJ, let Maven resolve dependencies, then run `Main.java`.

On first boot you should see:

```
[HikariCP] pool initialized
[Trie] 1025 names loaded
Pokédex ready
```

### 3. Login

Use the credentials configured in `docker-compose.yml`. Any failed connection surfaces as a dialog — if you see one, make sure Docker is running.

---

## Project Structure

```
src/main/java/
├── Main.java                  # Entry point — SwingUtilities.invokeLater bootstrap
├── PokedexUI.java             # Main window, SwingWorker orchestration, all panels
├── DatabaseManager.java       # HikariCP pool, all SQL queries, ACID transactions
└── PokemonTrie.java           # Standalone O(M) prefix tree for autocomplete
```

---

## Database Schema (key tables)

| Table | Purpose |
|---|---|
| `pokemon` | 1,000+ records; `PRIMARY KEY (number)`, `UNIQUE (name)` |
| `teams` | User-created teams; `idx_tm_team` composite index |
| `team_members` | Join table linking teams to Pokémon |
| `evolution` | Evolution chain data; queried via multi-table JOIN |
