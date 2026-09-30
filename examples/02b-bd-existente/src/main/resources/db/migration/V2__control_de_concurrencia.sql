-- Primera migración propia del equipo sobre el esquema heredado (versión 1).
-- Añade una columna de versión para el bloqueo optimista de JPA (@Version).

ALTER TABLE TB_TAREA ADD COLUMN NU_VERSION INTEGER NOT NULL DEFAULT 0;

-- Las otras aplicaciones que escriben en TB_TAREA no conocen NU_VERSION:
-- es el trigger quien la incrementa en todo UPDATE, venga de quien venga.
CREATE OR REPLACE FUNCTION FN_TR_TAREA_MODIFICADA() RETURNS TRIGGER AS $$
BEGIN
    NEW.FH_MODIFICACION := now();
    NEW.NU_VERSION := OLD.NU_VERSION + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
