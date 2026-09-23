-- Initial schema of the room reservation API

CREATE TABLE buildings (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             VARCHAR(100) NOT NULL UNIQUE COLLATE NOCASE,
    number_of_floors INTEGER      NOT NULL CHECK (number_of_floors BETWEEN 1 AND 200)
);

CREATE TABLE equipment (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    code  VARCHAR(50)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

CREATE TABLE rooms (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(100) NOT NULL UNIQUE COLLATE NOCASE,
    building_id INTEGER      NOT NULL REFERENCES buildings (id),
    floor       INTEGER      NOT NULL CHECK (floor >= 0),
    capacity    INTEGER      NOT NULL CHECK (capacity > 0),
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'MAINTENANCE'))
);

-- Equipment available in each room
CREATE TABLE room_equipment (
    room_id      INTEGER NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    equipment_id INTEGER NOT NULL REFERENCES equipment (id),
    PRIMARY KEY (room_id, equipment_id)
);

CREATE TABLE organizers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(150) NOT NULL,
    email       VARCHAR(254) NOT NULL UNIQUE COLLATE NOCASE,
    building_id INTEGER      NOT NULL REFERENCES buildings (id),
    floor       INTEGER      NOT NULL CHECK (floor >= 0)
);

CREATE TABLE reservations (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    title                  VARCHAR(200) NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    room_id                INTEGER      NOT NULL REFERENCES rooms (id),
    organizer_id           INTEGER      NOT NULL REFERENCES organizers (id),
    start_at               TIMESTAMP    NOT NULL,
    end_at                 TIMESTAMP    NOT NULL,
    number_of_participants INTEGER      NOT NULL CHECK (number_of_participants > 0),
    created_at             TIMESTAMP    NOT NULL,
    CHECK (start_at < end_at)
);

-- Equipment required by each reservation
CREATE TABLE reservation_equipment (
    reservation_id INTEGER NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    equipment_id   INTEGER NOT NULL REFERENCES equipment (id),
    PRIMARY KEY (reservation_id, equipment_id)
);

-- Speeds up the overlap check and the reservation filters
CREATE INDEX idx_reservations_room_period ON reservations (room_id, start_at, end_at);
CREATE INDEX idx_reservations_organizer ON reservations (organizer_id);
