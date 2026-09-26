# Meeting Room Reservation API

[![CI](https://github.com/CyprienJ/meetingRoomReservation-RMA/actions/workflows/ci.yml/badge.svg)](https://github.com/CyprienJ/meetingRoomReservation-RMA/actions/workflows/ci.yml)

REST API that lets a university manage its buildings, rooms, equipment and organizers,
and book meeting rooms, either a room chosen by the user or the most suitable room
picked automatically.

**Author:** Ronald Marin Cardona

The HTTP contract (endpoints, bodies, error codes) is defined in [`openapi.yml`](openapi.yml).
The original assignment, in French, is kept at the [end of this file](#backend-de-réservation-de-salles).

## Table of contents

1. [Tech stack](#tech-stack)
2. [Getting started](#getting-started)
3. [Running the tests and the linter](#running-the-tests-and-the-linter)
4. [API overview](#api-overview)
5. [Quick start with curl](#quick-start-with-curl)
6. [Error format](#error-format)
7. [Business rules](#business-rules)
8. [Project structure](#project-structure)
9. [Continuous integration](#continuous-integration)

## Tech stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 (Web MVC, Validation) |
| Persistence | Spring Data JPA, Hibernate, SQLite |
| Schema migrations | Flyway (`src/main/resources/db/migration`) |
| Boilerplate reduction | Lombok |
| Tests | JUnit 5, Mockito, Spring MockMvc |
| Linter | Checkstyle (`checkstyle.xml`) |
| Build | Maven, through the Maven wrapper (`mvnw`) |

## Getting started

### Prerequisites

Only a **JDK 21** is required. The Maven wrapper downloads Maven and every dependency
on the first run, so Maven does not need to be installed.

```bash
java -version   # must print 21 or later
```

### Run the API

```bash
# Linux / macOS / Git Bash
./mvnw spring-boot:run

# Windows (cmd or PowerShell)
mvnw.cmd spring-boot:run
```

The API listens on **http://localhost:8080**. If that port is already used by another
program, choose another one:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

To build a standalone jar instead:

```bash
./mvnw package -DskipTests
java -jar target/reservation-0.0.1-SNAPSHOT.jar
```

### Database

- The data is stored in the SQLite file **`reservation.db`**, created in the working
  directory on the first start. It is ignored by git.
- The tables are created and upgraded by **Flyway** from the scripts in
  `src/main/resources/db/migration`. Hibernate only validates that the entities match
  the schema (`spring.jpa.hibernate.ddl-auto=validate`).
- To start again from an empty database, stop the API and delete `reservation.db`.

## Running the tests and the linter

```bash
./mvnw test                 # unit and integration tests
./mvnw checkstyle:check     # linter
```

- **Unit tests** (`src/test/java/.../services`, `.../models`, `.../exceptions`) check the
  business rules without any database: repositories are mocked with Mockito, and the room
  ranking and automatic assignment are tested on objects built in memory.
- **Integration tests** (`src/test/java/.../controllers`) call the real HTTP endpoints
  with MockMvc against an **in-memory SQLite database** (profile `test`), so the
  `reservation.db` file is never touched. Each test is rolled back at the end.
- Every test follows the **GIVEN / WHEN / THEN** structure.
- The period rule "not in the past" relies on an injected `Clock`, which the unit tests
  fix to a known date.

## API overview

All endpoints are under `/api`. Dates use ISO 8601 with an offset
(`2030-10-15T14:00:00+02:00` or `2030-10-15T12:00:00Z`) and are returned in UTC.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/buildings` | Create a building |
| `GET` | `/api/buildings` | List the buildings, sorted by name |
| `GET` | `/api/buildings/{buildingId}` | Get a building |
| `PUT` | `/api/buildings/{buildingId}` | Update a building (floors cannot be removed while occupied) |
| `POST` | `/api/equipment` | Create a piece of equipment |
| `GET` | `/api/equipment` | List the equipment, sorted by code |
| `POST` | `/api/rooms` | Create a room |
| `GET` | `/api/rooms` | List the rooms, sorted by name |
| `GET` | `/api/rooms/available` | Search the rooms available and compatible over a period |
| `GET` | `/api/rooms/{roomId}` | Get a room |
| `PUT` | `/api/rooms/{roomId}` | Update the name, location and capacity of a room |
| `PATCH` | `/api/rooms/{roomId}/status` | Put a room in `AVAILABLE` or `MAINTENANCE` |
| `PUT` | `/api/rooms/{roomId}/equipment` | Replace all the equipment of a room |
| `POST` | `/api/organizers` | Create an organizer |
| `GET` | `/api/organizers` | List the organizers, sorted by name then id |
| `GET` | `/api/organizers/{organizerId}` | Get an organizer |
| `POST` | `/api/reservations` | Book a room chosen by the user |
| `POST` | `/api/reservations/automatic` | Book the most suitable room, chosen by the API |
| `GET` | `/api/reservations` | List reservations, with optional filters `roomId`, `organizerId`, `from`, `to` |
| `GET` | `/api/reservations/{reservationId}` | Get a reservation |
| `PATCH` | `/api/reservations/{reservationId}/cancel` | Cancel a reservation |

Every creation returns **201 Created** with the URI of the new resource in the
`Location` header.

## Quick start with curl

This scenario can be run from top to bottom on an empty database.

> **Note:** in a URL, `+` means a space. Inside query parameters, write the `+` of a
> date offset as **`%2B`**, or use UTC dates ending with `Z`.

```bash
# Two buildings
curl -X POST http://localhost:8080/api/buildings -H "Content-Type: application/json" \
     -d '{"name": "Bâtiment A", "numberOfFloors": 5}'
curl -X POST http://localhost:8080/api/buildings -H "Content-Type: application/json" \
     -d '{"name": "Bâtiment B", "numberOfFloors": 3}'

# Equipment
curl -X POST http://localhost:8080/api/equipment -H "Content-Type: application/json" \
     -d '{"code": "PROJECTOR", "label": "Vidéoprojecteur"}'
curl -X POST http://localhost:8080/api/equipment -H "Content-Type: application/json" \
     -d '{"code": "WHITEBOARD", "label": "Tableau blanc"}'

# Three rooms
curl -X POST http://localhost:8080/api/rooms -H "Content-Type: application/json" \
     -d '{"name": "Orion", "buildingId": 1, "floor": 2, "capacity": 30, "equipmentCodes": ["PROJECTOR", "WHITEBOARD"]}'
curl -X POST http://localhost:8080/api/rooms -H "Content-Type: application/json" \
     -d '{"name": "Vega", "buildingId": 1, "floor": 1, "capacity": 12}'
curl -X POST http://localhost:8080/api/rooms -H "Content-Type: application/json" \
     -d '{"name": "Atlas", "buildingId": 2, "floor": 0, "capacity": 25, "equipmentCodes": ["PROJECTOR"]}'

# An organizer working on floor 1 of building A
curl -X POST http://localhost:8080/api/organizers -H "Content-Type: application/json" \
     -d '{"name": "Alice Martin", "email": "alice.martin@example.org", "buildingId": 1, "floor": 1}'

# Rooms with a projector available for 10 people (returns Atlas, then Orion)
curl "http://localhost:8080/api/rooms/available?start=2030-10-15T14:00:00%2B02:00&end=2030-10-15T16:00:00%2B02:00&capacity=10&equipment=PROJECTOR"

# Book Orion
curl -X POST http://localhost:8080/api/reservations -H "Content-Type: application/json" \
     -d '{"title": "Soutenance de projet", "roomId": 1, "organizerId": 1, "start": "2030-10-15T14:00:00+02:00", "end": "2030-10-15T16:00:00+02:00", "numberOfParticipants": 25, "requiredEquipmentCodes": ["PROJECTOR"]}'

# Let the API choose a room for 10 people (returns Vega, on the organizer floor)
curl -X POST http://localhost:8080/api/reservations/automatic -H "Content-Type: application/json" \
     -d '{"title": "Point hebdomadaire", "organizerId": 1, "start": "2030-10-15T14:00:00+02:00", "end": "2030-10-15T15:00:00+02:00", "numberOfParticipants": 10}'

# Reservations of Alice on October 15th, then cancel the first one
curl "http://localhost:8080/api/reservations?organizerId=1&from=2030-10-15T00:00:00Z&to=2030-10-16T00:00:00Z"
curl -X PATCH http://localhost:8080/api/reservations/1/cancel

# Put Vega in maintenance
curl -X PATCH http://localhost:8080/api/rooms/2/status -H "Content-Type: application/json" \
     -d '{"status": "MAINTENANCE"}'
```

Example of a reservation returned by the API:

```json
{
  "id": 1,
  "title": "Soutenance de projet",
  "status": "CONFIRMED",
  "room": {
    "id": 1, "name": "Orion", "floor": 2, "capacity": 30, "status": "AVAILABLE",
    "building": { "id": 1, "name": "Bâtiment A", "numberOfFloors": 5 }
  },
  "organizer": {
    "id": 1, "name": "Alice Martin", "floor": 1,
    "building": { "id": 1, "name": "Bâtiment A", "numberOfFloors": 5 }
  },
  "start": "2030-10-15T12:00:00Z",
  "end": "2030-10-15T14:00:00Z",
  "numberOfParticipants": 25,
  "requiredEquipmentCodes": ["PROJECTOR"],
  "createdAt": "2026-09-26T15:33:13.592Z"
}
```

## Error format

Every error has the same body (`ApiErrorResponse` in the contract). For example, when
booking a room that is already taken:

```json
{
  "code": "ROOM_ALREADY_RESERVED",
  "message": "La salle Orion est déjà réservée sur cette période",
  "timestamp": "2026-09-26T15:33:13.668Z",
  "path": "/api/reservations",
  "details": { "roomId": 1, "conflictingReservationId": 1 },
  "fieldErrors": {}
}
```

| Code | HTTP | Meaning |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | One or more fields are invalid; see `fieldErrors` |
| `INVALID_RESERVATION_PERIOD` | 400 | The period is in the past, reversed or longer than eight hours |
| `BUILDING_NOT_FOUND` | 404 | The building does not exist |
| `ROOM_NOT_FOUND` | 404 | The room does not exist |
| `ORGANIZER_NOT_FOUND` | 404 | The organizer does not exist |
| `RESERVATION_NOT_FOUND` | 404 | The reservation does not exist |
| `EQUIPMENT_NOT_FOUND` | 404 | An equipment code does not exist |
| `ROOM_CAPACITY_EXCEEDED` | 409 | The chosen room is too small |
| `MISSING_REQUIRED_EQUIPMENT` | 409 | The chosen room lacks some requested equipment |
| `ROOM_ALREADY_RESERVED` | 409 | A confirmed reservation overlaps the period |
| `ROOM_UNAVAILABLE` | 409 | The chosen room is in maintenance |
| `NO_COMPATIBLE_ROOM` | 409 | No room fits the automatic reservation |
| `RESERVATION_ALREADY_CANCELLED` | 409 | The reservation is already cancelled |
| `RESOURCE_ALREADY_EXISTS` | 409 | A unique name, code or email is already used |
| `BUILDING_FLOOR_COUNT_CONFLICT` | 409 | A room or an organizer is on a floor that would be removed |

## Business rules

**Locations.** Floors are numbered from `0` (ground floor) to `numberOfFloors - 1`.
Building names, room names and organizer emails are unique, without considering case
(accents included: `Bâtiment A` and `BÂTIMENT A` are the same name).

**Period.** The start must be strictly before the end, must not be in the past, and a
reservation lasts at most eight hours.

**Compatibility.** A room can host a reservation when:

- its status is `AVAILABLE` (a room in maintenance is never proposed nor booked);
- `numberOfParticipants <= capacity` (a room of 30 seats hosts exactly 30 people);
- it has every requested piece of equipment (extra equipment is allowed);
- no **confirmed** reservation overlaps the period, where two periods overlap when
  `existing.start < new.end` and `existing.end > new.start`. Consecutive reservations
  (10:00-11:00 then 11:00-12:00) are allowed, and cancelled reservations are ignored.

**Chosen room.** `POST /api/reservations` books the requested room only if every rule
holds. Otherwise it is refused with the matching error code; the room is never replaced
by another one.

**Automatic assignment.** `POST /api/reservations/automatic` keeps the compatible rooms
and gives each of them a score, the lowest being the best:

```text
unusedCapacity = capacity - numberOfParticipants
distance       = |roomFloor - organizerFloor|          (same building)
distance       = 10 + |roomFloor - organizerFloor|     (other building)
score          = distance * 10 + unusedCapacity
```

One floor of distance is worth ten empty seats, and changing building adds 100 points.
Ties are broken by room name ignoring case, then by id, so the result is deterministic.
With the quick start data, for Alice (building A, floor 1) and 10 people:

| Room | Location | Capacity | Distance | Unused seats | Score |
|---|---|---:|---:|---:|---:|
| Vega | building A, floor 1 | 12 | 0 | 2 | **2** |
| Orion | building A, floor 2 | 30 | 1 | 20 | 30 |
| Atlas | building B, floor 0 | 25 | 11 | 15 | 125 |

The choice and the booking happen in the same transaction. When no room is compatible,
`NO_COMPATIBLE_ROOM` is returned and nothing is created.

**Availability search.** `GET /api/rooms/available` applies the same compatibility rules
and sorts the rooms by unused seats, then name ignoring case, then id. It never books
anything and returns an empty list when no room matches.

## Project structure

```text
src/main/java/fr/emse/ismin/reservation/
├── ReservationApplication.java   Entry point and Clock bean
├── controllers/                  HTTP endpoints (@RestController), map DTOs to services
├── services/                     Business rules (@Service), including RoomAllocator
├── repositories/                 Spring Data JPA interfaces (@Repository)
├── models/                       JPA entities (@Entity) and enums
├── dtos/                         Request and response records of the OpenAPI contract
└── exceptions/                   Business exceptions and the global error handler
src/main/resources/
├── application.properties        SQLite, Flyway and JPA configuration
└── db/migration/                 Flyway scripts (V1, V2, ...)
src/test/java/fr/emse/ismin/reservation/
├── services/                     Unit tests of the business rules
├── controllers/                  Integration tests of the endpoints
└── ...                           Other unit tests (models, exceptions)
```

A request goes through the layers seen in class: the **controller** receives the HTTP
call and validates the body, the **service** applies the business rules, the
**repository** reads and writes the database through JPA, and the **exception handler**
turns every error into the common error format.

## Continuous integration

The GitHub Actions workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs
on every push to `main`, on every pull request targeting `main`, and on demand. It has
two jobs:

| Job | Command | Fails when |
|---|---|---|
| `lint` | `./mvnw checkstyle:check` | a naming, Javadoc, import, brace or formatting rule is broken |
| `test` | `./mvnw test` | a unit or integration test fails (reports are kept as an artifact) |

---

# Backend de réservation de salles

## 1. Contexte

Une université souhaite disposer d'une API REST permettant de gérer des salles et leurs réservations.

Un utilisateur peut consulter les salles disponibles pour une période, réserver une salle précise ou laisser l'application sélectionner automatiquement la salle la plus adaptée à son besoin.

Le contrat détaillé de l'API, incluant les endpoints, les types attendus et les erreurs possibles, est défini dans [`openapi.yml`](openapi.yml).

## 2. Fonctionnalités attendues

L'application doit permettre :

- de créer, consulter et modifier des bâtiments ;
- de créer, consulter et modifier des salles ;
- de définir les équipements disponibles dans chaque salle ;
- de créer et consulter des organisateurs ;
- de rechercher les salles disponibles sur une période ;
- de réserver une salle précise ;
- de demander l'attribution automatique d'une salle ;
- d'annuler une réservation ;
- de consulter les réservations, avec des filtres par salle, organisateur ou période ;
- de placer temporairement une salle en maintenance.

## 3. Données manipulées

Les structures JSON échangées avec le front (bâtiments, salles, équipements,
organisateurs, réservations et erreurs) sont définies dans la section
`components.schemas` du fichier [`openapi.yml`](openapi.yml). Le README ne fixe pas
la forme des objets internes qui ne font pas partie du contrat HTTP.

Les relations et contraintes utiles au métier sont les suivantes :

- une salle et un organisateur sont localisés dans un bâtiment et à un étage ;
- le rez-de-chaussée porte le numéro `0` et, pour un bâtiment de `n` étages, un
  étage valide est compris entre `0` et `n - 1` ;
- la localisation de l'organisateur sert à calculer la proximité des salles lors
  d'une attribution automatique ;
- une salle en maintenance ne peut ni être proposée ni être réservée ;
- une réservation annulée reste consultable, mais ne bloque plus la salle.

## 4. Règles métier

### Période de réservation

- Le début doit être strictement antérieur à la fin.
- Le début ne doit pas être dans le passé.
- La durée ne peut pas dépasser huit heures.
- Les dates et heures sont transmises au format ISO 8601 avec un fuseau ou un décalage UTC, par exemple `2026-10-15T14:00:00+02:00`.

### Capacité et équipements

Le nombre de participants doit être strictement positif et inférieur ou égal à la capacité de la salle. Une salle de 30 places peut donc accueillir exactement 30 personnes.

La salle doit posséder tous les équipements demandés. La présence d'équipements supplémentaires est autorisée. Une liste absente ou vide signifie qu'aucun équipement particulier n'est exigé.

### Chevauchement

Deux réservations confirmées ne peuvent pas se chevaucher dans la même salle. Il y a chevauchement lorsque :

```text
reservationExistante.start < nouvelleReservation.end
ET
reservationExistante.end > nouvelleReservation.start
```

Deux réservations consécutives, par exemple `10:00–11:00` et `11:00–12:00`, sont autorisées. Les réservations annulées sont ignorées lors de cette vérification.

## 5. Choix d'une salle

### Salle choisie par l'utilisateur

Une salle explicitement choisie est retenue uniquement si elle existe, si son statut est `AVAILABLE`, si sa capacité est suffisante, si elle possède tous les équipements demandés et si aucune réservation confirmée ne chevauche la période demandée.

La demande est refusée dès qu'une condition n'est pas satisfaite. Le système ne remplace jamais silencieusement la salle choisie par une autre.

### Attribution automatique

Le système commence par ne conserver que les salles qui respectent toutes les conditions suivantes :

- statut `AVAILABLE` ;
- capacité suffisante ;
- présence de tous les équipements demandés ;
- absence de réservation confirmée en conflit avec la période.

Un score est calculé pour chaque salle compatible :

- `placesInutilisées = capacité - nombre de participants` ;
- lorsque les identifiants des bâtiments sont identiques, `distance = abs(étageSalle - étageOrganisateur)` ;
- dans des bâtiments différents, `distance = 10 + abs(étageSalle - étageOrganisateur)` ;
- `score = distance × 10 + placesInutilisées`.

Le score le plus faible est le meilleur. Cette pondération fait équivaloir un étage de distance à dix places inutilisées et ajoute une pénalité de 100 points lors d'un changement de bâtiment.

En cas d'égalité, les salles sont départagées par leur nom dans l'ordre alphabétique, sans tenir compte de la casse, puis par leur identifiant. Le résultat est ainsi déterministe.

Si aucune salle n'est compatible, aucune réservation n'est créée.

## 6. Tests obligatoires

### Tests unitaires

Le classement et l'attribution automatique doivent être testés indépendamment de la persistance :

- sélection d'une salle dont la capacité est exactement suffisante ;
- rejet d'une salle trop petite, en maintenance, déjà réservée ou sans un équipement demandé ;
- acceptation de deux réservations consécutives ;
- calcul de la distance dans un même bâtiment et entre deux bâtiments ;
- calcul du score combinant la distance et les places inutilisées ;
- sélection de la salle ayant le score le plus faible ;
- départage alphabétique, puis par identifiant ;
- absence de salle compatible.

### Tests d'intégration

- création et consultation d'une salle ;
- création d'une réservation et attribution automatique ;
- recherche de disponibilité et vérification de son ordre ;
- refus d'une réservation conflictuelle ;
- annulation puis nouvelle réservation sur la même période ;
- validation du format des réponses d'erreur.


### 7. Pour commencer

Vous pouvez utiliser https://start.spring.io/ pour génerer la structure du projet

### 8. Rappel de la grille de notation

| La Code                                                         | /14 |
|-----------------------------------------------------------------|----:|
| Chaque classe est au bon endroid                                |  /4 |
| Les endpoints demandés existent tous                            |  /4 |
| Le backend renvoie ce qu’on lui demande                         |  /2 |
| Les tests sont exhaustifs, et bien formulés (GIVEN, WHEN, THEN) |  /4 |
| Les erreurs sont gérées correctement                            |  /2 |
| Présence de commentaires (si pertinent)                         |  /1 |
| présence de javadoc                                             | /1 |
| Qualité globale du code (ex : noms des variables)               | /1 |

| La CI | /3 |
| --- |---:|
| job pour linter | /1 |
| job pour les tests | /1 |
| la ci tourne automatiquement sur main et est valide | /1 |

| La GitHub          | /3 |
|--------------------|---:|
| Les commits sont bien équilibrés et ont des noms explicites    | /2 |
| Un readme explique comment utiliser le projet | /1 |

