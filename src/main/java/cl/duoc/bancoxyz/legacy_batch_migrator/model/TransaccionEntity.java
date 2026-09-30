package cl.duoc.bancoxyz.legacy_batch_migrator.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TransaccionEntity {
    private int id;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;
    private String estado;
}
