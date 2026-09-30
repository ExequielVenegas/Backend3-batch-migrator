package cl.duoc.bancoxyz.legacy_batch_migrator.processor;

import cl.duoc.bancoxyz.legacy_batch_migrator.model.InteresDTO;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.InteresEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class InteresProcessor implements ItemProcessor<InteresDTO, InteresEntity> {
    private static final Map<String, BigDecimal> TASAS = Map.of(
            "ahorro", new BigDecimal("0.03"),
            "prestamo", new BigDecimal("0.05"),
            "hipoteca", new BigDecimal("0.04")
    );

    @Override
    public InteresEntity process(InteresDTO item) throws Exception {

        if (item.getSaldo() == null || item.getSaldo().signum() < 0) {
            throw new IllegalArgumentException("Saldo inválido para cuenta ID " + item.getCuentaId());
        }

        String tipoNormalizado = item.getTipo() == null ? "" : item.getTipo().trim().toLowerCase(Locale.ROOT);
        BigDecimal tasaInteres = TASAS.get(tipoNormalizado);
        if (tasaInteres == null) {
            throw new IllegalArgumentException("Tipo de cuenta no reconocido: " + item.getTipo());
        }

        log.info("Calculando intereses para cuenta ID: {} (Tipo: {})", item.getCuentaId(), tipoNormalizado);
        BigDecimal montoInteres = item.getSaldo().multiply(tasaInteres).setScale(2, RoundingMode.HALF_UP);

        return InteresEntity.builder()
                .cuentaId(item.getCuentaId())
                .nombre(item.getNombre())
                .tipo(tipoNormalizado)
                .saldoOriginal(item.getSaldo())
                .interesAplicado(montoInteres)
                .saldoFinal(item.getSaldo().add(montoInteres))
                .build();
    }
}
