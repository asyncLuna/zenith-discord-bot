# Zenith Discord Bot

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![Discord4J](https://img.shields.io/badge/Discord4J-3.3.3-5865F2?logo=discord&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-8.3-47A248?logo=mongodb&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle&logoColor=white)

Zenith is a reactive Spring Boot Discord bot for community utilities, Overwatch data, and server-specific automation.

The project is organized as a modular monolith: Discord events enter through Discord4J, commands and listeners delegate to focused feature modules, and shared services handle persistence, integrations, localization, and error reporting.

## Architecture

```mermaid
flowchart LR
    Discord[Discord gateway]
    Bot[Discord4J client]
    Commands[Commands and event listeners]
    Core[Core services and feature modules]
    Mongo[(MongoDB<br/>persistent state)]
    Overfast[OverFast API<br/>Overwatch data]
    Animality[Animality API<br/>images and facts]
    Quartz[Quartz scheduler<br/>Member of the Week]
    Sentry[Sentry<br/>optional observability]

    Discord <--> Bot
    Bot --> Commands
    Commands --> Core
    Core <--> Mongo
    Core --> Overfast
    Core --> Animality
    Quartz --> Core
    Core -. errors, traces, logs .-> Sentry
```

### Processing flows

1. Discord sends gateway events to the Discord4J client.
2. The command registry synchronizes global slash commands when the application starts.
3. Commands and component listeners execute feature workflows using reactive services.
4. Guild settings, account links, uwu locks, voting rounds, and votes are stored in MongoDB.
5. Overwatch and animal commands call their external APIs through WebClient-backed integrations with in-memory TTL caches.
6. Quartz opens, expires, and rotates Member of the Week voting rounds and the bot announces results in Discord.

MongoDB is the durable store for application state. External APIs are used for read-only game and animal content, while Sentry is optional and can be disabled by leaving its DSN empty.

## Features

- Global Discord slash-command registration with a small annotation-based command framework
- Overwatch player summaries, competitive ranks, hero data, hero statistics, maps, and top-hero statistics
- Discord-to-Overwatch account linking
- Anonymous confessions with optional replies, publishing channels, and author logs
- Member of the Week voting rounds with one-vote-per-member validation and scheduled rotation
- Server settings for confessions, voting, and moderation logs
- Message purging, colored role creation, predefined button workflows, and uwu locks
- Random animal images and facts
- English and Polish localization
- Reactive MongoDB persistence and WebClient integrations
- Optional Sentry error reporting, tracing, profiling, and log collection
- Docker Compose deployment with MongoDB and constrained container resources

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Discord | Discord4J 3.3.3 |
| Reactive runtime | Project Reactor, Spring WebFlux |
| Persistence | Spring Data MongoDB Reactive |
| Database | MongoDB 8.3.4 in Docker Compose |
| Scheduling | Quartz |
| External data | OverFast API, Animality API |
| Observability | Sentry Spring Boot integration |
| Testing | JUnit 5, Spring Boot Test, MongoDB reactive test support |
| Build | Gradle Kotlin DSL |

## Prerequisites

- JDK 21
- Docker Engine with Docker Compose, or an accessible MongoDB instance
- A Discord application and bot token
- A bot installation with the permissions required by the enabled commands

Zenith requests the `GUILDS`, `GUILD_MEMBERS`, `GUILD_MESSAGES`, `MESSAGE_CONTENT`, and `GUILD_MODERATION` gateway intents. Enable privileged intents in the Discord Developer Portal where required.

## Quick start

For local development, set the Discord token and a MongoDB URI reachable from your machine, then run:

```powershell
$env:DISCORD_BOT_TOKEN = "your-discord-bot-token"
$env:SPRING_MONGODB_URI = "mongodb://zenith:change-this-password@localhost:27017/zenith?authSource=admin"
$env:SPRING_PROFILES_ACTIVE = "dev"
.\gradlew.bat bootRun
```

The bot connects to Discord and synchronizes global slash commands during startup. Global command changes can take time to propagate through Discord.

The MongoDB service in Docker Compose is only available to containers on its private network; it does not publish port `27017` to the host. To run the application directly with `bootRun`, use a separately accessible MongoDB instance. To start the bot and MongoDB together, use the Docker Compose instructions below.

## Commands

| Command | Purpose |
| --- | --- |
| `/animal` | Send a random animal image and fact. |
| `/confessions` | Enable or disable confession submissions. |
| `/create_role` | Create a colored role and assign it to a user. |
| `/hero_data` | Get data for an Overwatch hero. |
| `/hero_stats` | Browse statistics for a specific hero. |
| `/link` | Link an Overwatch account to a Discord account. |
| `/map` | Browse maps for an Overwatch game mode. |
| `/member_of_the_week` | Open, close, rotate, pause, or inspect voting rounds. |
| `/ping` | Check the bot's latency. |
| `/player_summary` | View an Overwatch player's summary and competitive ranks. |
| `/purge_messages` | Delete messages from a member. |
| `/send_button` | Send a predefined button workflow. |
| `/settings` | Configure Zenith for a server. |
| `/top_heroes` | View a player's most-played Overwatch heroes. |
| `/uwulock` | Manage user uwu blocks. |

## Configuration

The application reads secrets and connection details from environment variables:

| Variable | Required | Description |
| --- | --- | --- |
| `DISCORD_BOT_TOKEN` | Yes | Discord bot token. |
| `SPRING_MONGODB_URI` | Yes | MongoDB connection URI. |
| `SPRING_PROFILES_ACTIVE` | No | Use `dev` for development logging or `prod` for production settings. |
| `SENTRY_DSN` | No | Sentry DSN; empty disables event reporting. |
| `SENTRY_SEND_DEFAULT_PII` | No | Whether Sentry may send default PII; defaults to `false`. |
| `SENTRY_LOGS_ENABLED` | No | Whether Sentry log collection is enabled. |

Member of the Week settings are under `zenith.member-of-the-week` in `src/main/resources/application.yaml` and can be overridden with Spring configuration properties.

| Property | Default | Description |
| --- | --- | --- |
| `guild-id` | configured in `application.yaml` | Guild used by the scheduled voting workflow. |
| `role-id` | configured in `application.yaml` | Role assigned to the current Member of the Week winner. |
| `voting-notification-role-id` | `1526317499862356018` | Role pinged when a voting round opens; leave blank to disable the ping. |
| `timezone` | `Europe/Warsaw` | Time zone used for scheduling and display. |
| `cron` | `0 0 18 ? * MON` | Opens a round on Mondays at 18:00. |
| `voting-duration` | `24h` | Duration of an automatic voting round. |

Do not commit `.env` files, bot tokens, database passwords, or Sentry DSNs.

## Docker Compose

Copy and edit `.env` to set the Discord token and MongoDB credentials. Docker Compose reads this file; a locally launched Gradle process does not load it automatically. Then build the application JAR and start MongoDB and the bot:

```powershell
Copy-Item .env.example .env
.\gradlew.bat clean bootJar
docker compose up --build -d
```

View logs with:

```powershell
docker compose logs -f java
```

Stop the services with:

```powershell
docker compose down
```

MongoDB data is stored in the named `mongo-data` volume. Removing that volume permanently discards the local database.

## Build and testing

Check formatting, run the tests, and build the executable Spring Boot JAR with:

```powershell
.\gradlew.bat spotlessCheck clean test bootJar
```

The JAR is written to `build/libs/`. The tests cover the Spring application context, uwulock behavior, and Member of the Week failure handling. The context test needs MongoDB configured through `SPRING_MONGODB_URI`; it mocks the Discord gateway and does not need a bot token.

GitHub Actions runs formatting checks, tests, and a JAR build on pushes, pull requests, and manual runs. The workflow uses Ubuntu 24.04, Temurin JDK 21.0.12+8.0.LTS, the checked-in Gradle wrapper, and an ephemeral MongoDB 7.0.41 service. CI uses MongoDB 7 because MongoDB 8.3.4 currently refuses to start on the GitHub-hosted runner kernel (SERVER-121912); the service is only for tests and does not change the MongoDB 8.3.4 version used by Docker Compose. Do not downgrade an existing MongoDB deployment or reuse its data directory with an older MongoDB image. Pull requests and runs on non-default branches do not publish artifacts or releases. A successful push to the default branch or manual run on that branch uploads the tested JAR as a workflow artifact and creates a GitHub Release and matching tag.

Each release version is the base version in `build.gradle.kts` followed by the GitHub Actions run number and rerun attempt, for example `0.4.0.111.1`. The release includes the exact JAR tested by that workflow, a `.sha256` checksum, and release notes listing every commit subject since the previous tag (or all available commit history for the first release). Run numbers distinguish workflow runs; the attempt suffix distinguishes reruns. Workflow artifacts are retained for 14 days, while release assets remain attached to the GitHub Release.

To reproduce a versioned archive locally, use the same source commit, JDK, Gradle wrapper, dependency lock state, and build version:

```powershell
.\gradlew.bat clean bootJar -PbuildVersion=0.4.0.111.1
```

Archive entry timestamps and ordering are normalized, and resolved project dependencies are locked in `gradle.lockfile`. The release process publishes the already-tested build rather than rebuilding it. Changing the build version changes the JAR name and embedded version, so different run numbers or attempts intentionally produce different hashes; the hosted Ubuntu image can also receive updates, so bit-for-bit reproducibility across different runner-image revisions is not guaranteed.

## Project structure

```text
src/main/java/dev/asyncluna/zenith
├── core
│   ├── integration       # OverFast and Animality API clients and caches
│   ├── i18n              # English and Polish localization
│   ├── model             # Shared MongoDB documents
│   ├── repository        # Reactive MongoDB repositories
│   └── service           # Shared application services
├── discord
│   ├── client             # Discord4J client and gateway intents
│   ├── command            # Command annotations, registry, dispatch, and errors
│   ├── feature            # Discord commands and component listeners
│   └── listener            # Gateway and guild event listeners
├── memberoftheweek        # Voting domain, notifications, and Quartz jobs
└── ZenithDiscordBotApplication.java

src/main/resources
├── application.yaml       # Base configuration
├── application-dev.yaml   # Development profile
├── application-prod.yaml  # Production profile
├── messages.properties    # English translations
└── messages_pl.properties # Polish translations
```

## Contributing

Run `bash ./gradlew test` (or `gradlew.bat test` on Windows) before opening a pull request. Keep secrets and local configuration untracked, and add tests for behavior changes where practical.

## License

This project is licensed under the [MIT License](LICENSE).
