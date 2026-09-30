package cl.duoc.bancoxyz.legacy_batch_migrator.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CuentaAnualDTO {
    private int cuentaId;
    private String fecha;
    private String transaccion;
    private BigDecimal monto;
    private String descripcion;
}
