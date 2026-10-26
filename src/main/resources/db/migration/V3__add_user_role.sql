-- What an account may do. Admins can also use /api/admin (for example, to run the
-- collection by hand). Every existing account becomes a USER.
ALTER TABLE users
    ADD COLUMN role text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'));
