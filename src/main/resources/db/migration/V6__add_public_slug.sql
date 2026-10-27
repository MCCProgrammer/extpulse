-- The address of a user's public page and badges (/p/{public_slug}). Empty until the
-- user chooses one: nothing about an account is public by default.
ALTER TABLE users
    ADD COLUMN public_slug text UNIQUE
        CHECK (public_slug ~ '^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$');
