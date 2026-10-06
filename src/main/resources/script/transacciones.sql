
CREATE TABLE IF NOT EXISTS transacciones_diarias
(
    id
    INT
    PRIMARY
    KEY,
    fecha
    DATE,
    monto
    DECIMAL
(
    10,
    2
),
    tipo VARCHAR
(
    50
),
    estado VARCHAR
(
    50
)
    );
