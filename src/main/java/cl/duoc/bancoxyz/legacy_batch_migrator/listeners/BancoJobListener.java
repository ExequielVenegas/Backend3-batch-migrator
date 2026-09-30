package cl.duoc.bancoxyz.legacy_batch_migrator.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;

@Slf4j
public class BancoJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("======================================================");
        log.info("INICIANDO BATCH JOB: {}", jobExecution.getJobInstance().getJobName());
        log.info("======================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("======================================================");
        log.info("FIN DEL BATCH JOB: {} | ESTADO FINAL: {}", jobExecution.getJobInstance().getJobName(), jobExecution.getStatus());

        long readCount = 0;
        long writeCount = 0;
        long skipCount = 0;

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            readCount += stepExecution.getReadCount();
            writeCount += stepExecution.getWriteCount();
            skipCount += stepExecution.getSkipCount();
        }

        log.info("Resumen Estadístico -> Leídos: {} | Escritos: {} | Omitidos (Saltados): {}", readCount, writeCount, skipCount);
        log.info("======================================================");
    }
}