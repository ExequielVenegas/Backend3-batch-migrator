
CREATE TABLE IF NOT EXISTS calculo_intereses
(
    cuenta_id
    INT
    PRIMARY
    KEY,
    nombre
    VARCHAR
(
    100
),
    tipo VARCHAR
(
    50
),
    saldo_original DECIMAL
(
    15,
    2
),
    interes_aplicado DECIMAL
(
    15,
    2
),
    saldo_final DECIMAL
(
    15,
    2
)
    );
