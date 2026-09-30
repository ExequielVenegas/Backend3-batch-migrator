package cl.duoc.bancoxyz.legacy_batch_migrator.policies;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.validation.BindException;

import java.time.format.DateTimeParseException;

@Slf4j
public class BancoSkipPolicy implements SkipPolicy {

    private static final int MAX_SKIPS = 1000;

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (skipCount >= MAX_SKIPS) {
            log.error("Se ha superado el límite de saltos ({}). Abortando el Job.", MAX_SKIPS);
            return false;
        }

        if (t instanceof FlatFileParseException || t instanceof BindException || (t.getCause() instanceof BindException)) {
            log.warn("SkipPolicy activada: Error de formato o enlace de datos en lectura CSV. Omitiendo registro. (Fallo {}/{})", skipCount + 1, MAX_SKIPS);
            return true;
        }

        if (t instanceof DateTimeParseException || (t.getCause() instanceof DateTimeParseException)) {
            log.warn("SkipPolicy activada: Error de formato de fecha. Omitiendo registro. (Fallo {}/{})", skipCount + 1, MAX_SKIPS);
            return true;
        }

        if (t instanceof IllegalArgumentException || t.getCause() instanceof IllegalArgumentException) {
            log.warn("SkipPolicy activada: Error de validación de negocio. Omitiendo registro. Motivo: {}", t.getMessage());
            return true;
        }

        log.error("Excepción no manejada por SkipPolicy: {}", t.getMessage());
        return false;
    }
}
