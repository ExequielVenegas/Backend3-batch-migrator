package cl.duoc.bancoxyz.legacy_batch_migrator.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InteresDTO {

    @EqualsAndHashCode.Include
    private int cuentaId;

    private String nombre;
    private BigDecimal saldo;
    private int edad;
    private String tipo;
}
