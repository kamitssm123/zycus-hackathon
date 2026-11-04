-- SlotMind core schema. Flyway owns this; Hibernate runs with ddl-auto=validate.
-- Never edit an applied migration — add V3__, V4__ ...

CREATE TABLE providers (
    id         varchar(16)  PRIMARY KEY,
    name       varchar(120) NOT NULL,
    specialty  varchar(16)  NOT NULL,
    status     varchar(16)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_providers_specialty CHECK (specialty IN ('GENERAL','SPECIALIST','PEDIATRIC')),
    CONSTRAINT ck_providers_status    CHECK (status    IN ('AVAILABLE','BUSY','OFFLINE'))
);

CREATE TABLE slots (
    id               varchar(16) PRIMARY KEY,
    provider_id      varchar(16) NOT NULL REFERENCES providers (id),
    start_time       timestamptz NOT NULL,
    duration_minutes integer     NOT NULL,
    status           varchar(16) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_slots_status   CHECK (status IN ('OPEN','BOOKED','CANCELLED')),
    CONSTRAINT ck_slots_duration CHECK (duration_minutes > 0)
);

-- A provider cannot hold two slots starting at the same instant.
CREATE UNIQUE INDEX uq_slots_provider_start ON slots (provider_id, start_time);
-- Drives the candidate-slot query in SchedulingEngine.
CREATE INDEX idx_slots_open_start ON slots (start_time) WHERE status = 'OPEN';

CREATE TABLE appointments (
    id           varchar(16)  PRIMARY KEY,
    patient_name varchar(120) NOT NULL,
    type         varchar(16)  NOT NULL,
    provider_id  varchar(16)  NOT NULL REFERENCES providers (id),
    slot_id      varchar(16)  NOT NULL REFERENCES slots (id),
    status       varchar(24)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_appointments_type   CHECK (type   IN ('ROUTINE','URGENT','FOLLOW_UP')),
    CONSTRAINT ck_appointments_status CHECK (status IN
        ('CONFIRMED','RESCHEDULE_PENDING','RESCHEDULED','COMPLETED'))
);

-- *** DOUBLE-BOOKING BACKSTOP ***
-- At most one live appointment may occupy a slot. Partial, so a COMPLETED
-- appointment does not permanently burn its slot id. Postgres-only: H2 cannot
-- express a partial index, which is why AcceptSuggestionConcurrencyIT exists.
CREATE UNIQUE INDEX uq_appointments_live_slot
    ON appointments (slot_id) WHERE status <> 'COMPLETED';

CREATE INDEX idx_appointments_provider_status ON appointments (provider_id, status);

CREATE TABLE reschedule_suggestions (
    id                    varchar(16)      PRIMARY KEY,
    appointment_id        varchar(16)      NOT NULL REFERENCES appointments (id),
    recommended_slot_id   varchar(16)      NOT NULL REFERENCES slots (id),
    alternate_provider_id varchar(16)          NULL REFERENCES providers (id),
    confidence            double precision NOT NULL,
    reasoning             text             NOT NULL,
    status                varchar(16)      NOT NULL,
    trigger_reason        varchar(24)      NOT NULL,
    strategy_key          varchar(32)      NOT NULL DEFAULT 'rule',
    fallback_used         boolean          NOT NULL DEFAULT false,
    fallback_reason       text                 NULL,
    created_at            timestamptz      NOT NULL DEFAULT now(),
    updated_at            timestamptz      NOT NULL DEFAULT now(),
    CONSTRAINT ck_sugg_status     CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT ck_sugg_trigger    CHECK (trigger_reason IN
        ('INITIAL','PROVIDER_OFFLINE','SLOT_CANCELLED')),
    CONSTRAINT ck_sugg_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_sugg_appointment_status ON reschedule_suggestions (appointment_id, status);

-- The agentic loop must not re-suggest for the same (appointment, trigger) while
-- one is still PENDING. App-level guard in RescheduleService; this is the backstop.
CREATE UNIQUE INDEX uq_pending_suggestion
    ON reschedule_suggestions (appointment_id, trigger_reason) WHERE status = 'PENDING';

-- Human-readable ids ("APT-001"). A mapped entity rather than a sequence so the
-- same mechanism works under Flyway/Postgres and Hibernate create-drop/H2.
CREATE TABLE id_counters (
    name       varchar(32) PRIMARY KEY,
    next_value bigint      NOT NULL
);
