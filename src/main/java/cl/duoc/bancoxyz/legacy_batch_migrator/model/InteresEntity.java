package cl.duoc.bancoxyz.legacy_batch_migrator.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InteresEntity {

    @EqualsAndHashCode.Include
    private int cuentaId;


    private String nombre;
    private String tipo;
    private BigDecimal saldoOriginal;
    private BigDecimal interesAplicado;
    private BigDecimal saldoFinal;
}
