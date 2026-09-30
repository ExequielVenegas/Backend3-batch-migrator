package cl.duoc.bancoxyz.legacy_batch_migrator.processor;

import cl.duoc.bancoxyz.legacy_batch_migrator.model.TransaccionEntity;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.TransaccionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
public class TransaccionProcessor implements ItemProcessor<TransaccionDTO, TransaccionEntity> {
    private static final Set<String> TIPOS_VALIDOS = Set.of("CREDITO", "DEBITO");
    private static final List<DateTimeFormatter> FORMATOS_FECHA = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    @Override
    public TransaccionEntity process(TransaccionDTO item) throws Exception {

        if (item.getMonto() == null || item.getMonto().signum() <= 0) {
            throw new IllegalArgumentException("Monto inválido en transacción ID " + item.getId());
        }

        LocalDate fechaParseada = parsearFechaFlexible(item.getFecha());
        String tipoNormalizado = item.getTipo() == null ? "" : item.getTipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_VALIDOS.contains(tipoNormalizado)) {
            throw new IllegalArgumentException("Tipo de transacción no reconocido: " + item.getTipo());
        }

        log.info("Procesando transacción válida ID: {}", item.getId());

        return TransaccionEntity.builder()
                .id(item.getId())
                .fecha(fechaParseada)
                .monto(item.getMonto())
                .tipo(tipoNormalizado)
                .estado("PROCESADO_OK")
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
