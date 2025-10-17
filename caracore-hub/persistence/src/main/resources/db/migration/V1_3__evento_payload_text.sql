-- Align column type with Hibernate mapping to avoid OID conversion attempts
ALTER TABLE evento
    ALTER COLUMN payload TYPE text;
