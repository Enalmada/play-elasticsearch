CREATE TABLE account (
    id         integer NOT NULL PRIMARY KEY,
    email      varchar NOT NULL UNIQUE,
    name       varchar NOT NULL,
    status     varchar NOT NULL
);