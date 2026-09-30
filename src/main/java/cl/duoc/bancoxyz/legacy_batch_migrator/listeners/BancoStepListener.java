package cl.duoc.bancoxyz.legacy_batch_migrator.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

@Slf4j
public class BancoStepListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info(">>> Iniciando Step: {} | Hilo: {}", stepExecution.getStepName(), Thread.currentThread().getName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("<<< Fin del Step: {} | Registros procesados en este step: {} | Hilo: {}",
                stepExecution.getStepName(), stepExecution.getWriteCount(), Thread.currentThread().getName());
        return stepExecution.getExitStatus();
    }
}