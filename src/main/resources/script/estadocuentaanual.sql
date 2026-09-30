DROP TABLE IF EXISTS estado_cuenta_anual;

CREATE TABLE IF NOT EXISTS estado_cuenta_anual
(
    id
    INT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    cuenta_id
    INT,
    fecha
    DATE,
    tipo_transaccion
    VARCHAR
(
    50
),
    monto DECIMAL
(
    10,
    2
),
    descripcion VARCHAR
(
    255
),
    auditado BOOLEAN
    );
