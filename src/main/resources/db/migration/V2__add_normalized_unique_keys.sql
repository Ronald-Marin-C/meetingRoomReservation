-- SQLite only folds the case of ASCII letters (COLLATE NOCASE, LOWER), so names
-- such as "Bâtiment A" and "BÂTIMENT A" were considered different. The application
-- now stores a lower-cased key computed in Java and uniqueness is checked on it.

ALTER TABLE buildings ADD COLUMN normalized_name VARCHAR(100) NOT NULL DEFAULT '';
UPDATE buildings SET normalized_name = LOWER(name);
CREATE UNIQUE INDEX ux_buildings_normalized_name ON buildings (normalized_name);

ALTER TABLE rooms ADD COLUMN normalized_name VARCHAR(100) NOT NULL DEFAULT '';
UPDATE rooms SET normalized_name = LOWER(name);
CREATE UNIQUE INDEX ux_rooms_normalized_name ON rooms (normalized_name);

ALTER TABLE organizers ADD COLUMN normalized_email VARCHAR(254) NOT NULL DEFAULT '';
UPDATE organizers SET normalized_email = LOWER(email);
CREATE UNIQUE INDEX ux_organizers_normalized_email ON organizers (normalized_email);
