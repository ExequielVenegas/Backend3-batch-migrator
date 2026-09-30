package cl.duoc.bancoxyz.legacy_batch_migrator.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;

@Slf4j
public class BancoSkipListener implements SkipListener<Object, Object> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("--- [SKIP EN LECTURA] Registro saltado por error: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        log.warn("--- [SKIP EN PROCESO] Registro saltado: {} | Motivo: {}", item.toString(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.warn("--- [SKIP EN ESCRITURA] Registro saltado: {} | Motivo: {}", item.toString(), t.getMessage());
    }
}