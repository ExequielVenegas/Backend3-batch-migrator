package cl.duoc.bancoxyz.legacy_batch_migrator.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransaccionDTO {
    private int id;
    private String fecha;
    private BigDecimal monto;
    private String tipo;
}
