-- El esquema cambia con una migración nueva, nunca editando V1 (que ya se aplicó).
-- El DEFAULT da valor a las filas que ya existan, para que el NOT NULL no falle.
ALTER TABLE tasks ADD COLUMN fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
