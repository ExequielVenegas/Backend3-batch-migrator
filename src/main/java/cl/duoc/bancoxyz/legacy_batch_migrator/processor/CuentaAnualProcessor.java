package cl.duoc.bancoxyz.legacy_batch_migrator.processor;

import cl.duoc.bancoxyz.legacy_batch_migrator.model.CuentaAnualDTO;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.CuentaAnualEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class CuentaAnualProcessor implements ItemProcessor<CuentaAnualDTO, CuentaAnualEntity> {
    private static final List<DateTimeFormatter> FORMATOS_FECHA = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    @Override
    public CuentaAnualEntity process(CuentaAnualDTO item) throws Exception {

        if (item.getMonto() == null || item.getMonto().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Monto inválido para cuenta ID " + item.getCuentaId());
        }

        LocalDate fechaParseada = parsearFechaFlexible(item.getFecha());
        log.info("Procesando registro anual para cuenta ID: {}", item.getCuentaId());

        return CuentaAnualEntity.builder()
                .cuentaId(item.getCuentaId())
                .fecha(fechaParseada)
                .tipoTransaccion(item.getTransaccion().toUpperCase())
                .monto(item.getMonto())
                .descripcion(item.getDescripcion())
                .auditado(true)
                .build();
    }

    private LocalDate parsearFechaFlexible(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(fechaStr.trim(), formatter);
            } catch (DateTimeParseException e) {
            }
        }
        throw new IllegalArgumentException("Formato de fecha no reconocido: " + fechaStr);
    }
}
