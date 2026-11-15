-- Roles gate features that aren't open to everyone yet. Everyone starts as USER; promote by hand on the server:
--   UPDATE users SET role = 'TESTER' WHERE username = '...';
-- The user row is loaded on every request, so a change applies on the next call (no new token needed).
ALTER TABLE users ADD COLUMN role VARCHAR NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'TESTER', 'ADMIN'));
