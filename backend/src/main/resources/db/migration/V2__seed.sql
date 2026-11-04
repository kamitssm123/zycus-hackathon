-- Demo seed (brief, Addendum A). Runs for `make up` and the walkthrough only —
-- spring.flyway.enabled=false in the test profile, so tests build their own
-- fixtures via TestFixtures. Two sources of truth, deliberately (DECISIONS D-15).
--
-- The brief's `NOW() + 60` is H2 syntax and is invalid on Postgres; translated
-- to real intervals below. SLT-001..006 and PRV/APT rows are verbatim otherwise.

INSERT INTO providers (id, name, specialty, status) VALUES
 ('PRV-001', 'Dr. Sarah Chen',     'GENERAL',    'BUSY'),
 ('PRV-002', 'Dr. James Okonkwo',  'SPECIALIST', 'AVAILABLE'),
 ('PRV-003', 'Dr. Priya Sharma',   'PEDIATRIC',  'BUSY'),
 ('PRV-004', 'Dr. Rahul Verma',    'GENERAL',    'AVAILABLE'),
 ('PRV-005', 'Dr. Ananya Iyer',    'SPECIALIST', 'BUSY');

INSERT INTO slots (id, provider_id, start_time, duration_minutes, status) VALUES
 ('SLT-001', 'PRV-001', NOW() + INTERVAL  '60 minutes', 30, 'BOOKED'),
 ('SLT-002', 'PRV-001', NOW() + INTERVAL '120 minutes', 30, 'BOOKED'),
 ('SLT-003', 'PRV-003', NOW() + INTERVAL  '90 minutes', 45, 'BOOKED'),
 ('SLT-004', 'PRV-002', NOW() + INTERVAL '180 minutes', 30, 'OPEN'),
 ('SLT-005', 'PRV-004', NOW() + INTERVAL '150 minutes', 30, 'OPEN'),
 ('SLT-006', 'PRV-005', NOW() + INTERVAL '240 minutes', 60, 'BOOKED'),
 -- Added beyond Addendum A: taking PRV-001 offline affects two appointments, and
 -- a one-open-GENERAL-slot clinic makes the second suggestion trivially empty.
 -- These give the engine a real choice to reason about.
 ('SLT-007', 'PRV-004', NOW() + INTERVAL '210 minutes', 30, 'OPEN'),
 ('SLT-008', 'PRV-004', NOW() + INTERVAL '270 minutes', 30, 'OPEN'),
 ('SLT-009', 'PRV-002', NOW() + INTERVAL '300 minutes', 30, 'OPEN'),
 ('SLT-010', 'PRV-003', NOW() + INTERVAL '330 minutes', 45, 'OPEN');

INSERT INTO appointments (id, patient_name, type, provider_id, slot_id, status) VALUES
 ('APT-001', 'John Martinez', 'ROUTINE',   'PRV-001', 'SLT-001', 'CONFIRMED'),
 ('APT-002', 'Emily Watson',  'FOLLOW_UP', 'PRV-001', 'SLT-002', 'CONFIRMED'),
 ('APT-003', 'Aisha Patel',   'URGENT',    'PRV-003', 'SLT-003', 'CONFIRMED'),
 ('APT-004', 'Robert Kim',    'ROUTINE',   'PRV-005', 'SLT-006', 'CONFIRMED');

-- Generated ids continue after the seeded block.
INSERT INTO id_counters (name, next_value) VALUES
 ('APPOINTMENT', 5),
 ('SUGGESTION',  1),
 ('SLOT',       11),
 ('PROVIDER',    6);
