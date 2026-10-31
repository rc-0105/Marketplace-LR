-- Datos básicos opcionales del perfil (RF-07, RF-08). Nulos para no tocar las
-- cuentas existentes; el formato lo valida la API.
ALTER TABLE usuario
    ADD COLUMN telefono VARCHAR(20),
    ADD COLUMN foto_url VARCHAR(500);
