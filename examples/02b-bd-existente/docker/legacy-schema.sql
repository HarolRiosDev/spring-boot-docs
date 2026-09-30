-- =====================================================================
-- Esquema heredado de la base de datos de tareas.
--
-- Simula una base de datos que ya existía antes de esta aplicación: la
-- creó y la mantiene otro equipo (un DBA) y otras aplicaciones escriben
-- en ella. La aplicación Spring Boot NO ejecuta nunca este script: lo
-- ejecuta Postgres al crear el contenedor, desde
-- /docker-entrypoint-initdb.d/ (en docker-compose.yml y en los tests).
-- =====================================================================

-- Los identificadores están en mayúsculas y sin comillas: Postgres los
-- guarda en minúsculas (TB_TAREA se convierte en tb_tarea).

CREATE SEQUENCE SQ_TAREA START WITH 1000 INCREMENT BY 1;

CREATE TABLE TB_TAREA (
    ID_TAREA        BIGINT        PRIMARY KEY DEFAULT nextval('SQ_TAREA'),
    DS_TITULO       VARCHAR(255)  NOT NULL,
    DS_DESCRIPCION  VARCHAR(1000),
    FL_COMPLETADA   CHAR(1)       NOT NULL DEFAULT 'N' CHECK (FL_COMPLETADA IN ('S', 'N')),
    FH_ALTA         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FH_MODIFICACION TIMESTAMP
);

-- Comentarios numerados por tarea: la clave primaria es compuesta
CREATE TABLE TB_COMENTARIO_TAREA (
    ID_TAREA  BIGINT       NOT NULL REFERENCES TB_TAREA (ID_TAREA),
    NU_LINEA  INTEGER      NOT NULL,
    DS_TEXTO  VARCHAR(500) NOT NULL,
    FH_ALTA   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_TAREA, NU_LINEA)
);

-- La fecha de última modificación la pone la propia base de datos
CREATE FUNCTION FN_TR_TAREA_MODIFICADA() RETURNS TRIGGER AS $$
BEGIN
    NEW.FH_MODIFICACION := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER TR_TAREA_MODIFICADA
    BEFORE UPDATE ON TB_TAREA
    FOR EACH ROW EXECUTE FUNCTION FN_TR_TAREA_MODIFICADA();

-- Resumen de cada tarea con sus comentarios, pensado para informes
CREATE VIEW VW_RESUMEN_TAREA AS
SELECT t.ID_TAREA,
       t.DS_TITULO,
       t.FL_COMPLETADA,
       COUNT(c.NU_LINEA) AS NU_COMENTARIOS,
       MAX(c.FH_ALTA)    AS FH_ULTIMO_COMENTARIO
  FROM TB_TAREA t
  LEFT JOIN TB_COMENTARIO_TAREA c ON c.ID_TAREA = t.ID_TAREA
 GROUP BY t.ID_TAREA, t.DS_TITULO, t.FL_COMPLETADA;

-- Borra las tareas completadas hace más de p_dias días (y sus comentarios)
-- y devuelve cuántas tareas ha borrado
CREATE FUNCTION FN_PURGAR_COMPLETADAS(p_dias INTEGER) RETURNS INTEGER AS $$
DECLARE
    v_borradas INTEGER;
BEGIN
    DELETE FROM TB_COMENTARIO_TAREA
     WHERE ID_TAREA IN (SELECT ID_TAREA
                          FROM TB_TAREA
                         WHERE FL_COMPLETADA = 'S'
                           AND FH_ALTA < now() - make_interval(days => p_dias));

    DELETE FROM TB_TAREA
     WHERE FL_COMPLETADA = 'S'
       AND FH_ALTA < now() - make_interval(days => p_dias);

    GET DIAGNOSTICS v_borradas = ROW_COUNT;
    RETURN v_borradas;
END;
$$ LANGUAGE plpgsql;

-- Datos que ya había en la base de datos (reciben los ids 1000 a 1005)
INSERT INTO TB_TAREA (DS_TITULO, DS_DESCRIPCION, FL_COMPLETADA, FH_ALTA) VALUES
    ('Migrar el servidor de correo',   'Pendiente desde el año pasado', 'S', now() - INTERVAL '400 days'),
    ('Revisar las copias de seguridad', NULL,                           'S', now() - INTERVAL '120 days'),
    ('Actualizar el inventario',       'Hojas de cálculo del almacén',  'S', now() - INTERVAL '3 days'),
    ('Preparar la auditoría',          NULL,                            'N', now() - INTERVAL '10 days'),
    ('Renovar los certificados',       'Caducan a final de mes',        'N', now() - INTERVAL '2 days'),
    ('Documentar la API interna',      NULL,                            'N', now());

INSERT INTO TB_COMENTARIO_TAREA (ID_TAREA, NU_LINEA, DS_TEXTO, FH_ALTA) VALUES
    (1001, 1, 'Las copias del lunes fallaron',      now() - INTERVAL '119 days'),
    (1001, 2, 'Resuelto cambiando el disco',        now() - INTERVAL '118 days'),
    (1003, 1, 'Falta la documentación del auditor', now() - INTERVAL '9 days'),
    (1003, 2, 'Reunión fijada para el jueves',      now() - INTERVAL '1 day');
