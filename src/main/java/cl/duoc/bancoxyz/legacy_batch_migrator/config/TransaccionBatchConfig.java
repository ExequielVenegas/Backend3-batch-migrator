package cl.duoc.bancoxyz.legacy_batch_migrator.config;

import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoJobListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoSkipListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoStepListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.TransaccionEntity;
import cl.duoc.bancoxyz.legacy_batch_migrator.processor.TransaccionProcessor;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.TransaccionDTO;
import cl.duoc.bancoxyz.legacy_batch_migrator.policies.BancoChunkCompletionPolicy;
import cl.duoc.bancoxyz.legacy_batch_migrator.policies.BancoRetryPolicy;
import cl.duoc.bancoxyz.legacy_batch_migrator.policies.BancoSkipPolicy;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class TransaccionBatchConfig {

    @Bean
    public ItemReader<TransaccionDTO> transaccionReader(@Value("${batch.input.transacciones:classpath:input/transacciones.csv}") Resource input) {
        var delegate = new FlatFileItemReaderBuilder<TransaccionDTO>()
                .name("transaccionReader")
                .resource(input)
                .linesToSkip(1)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .targetType(TransaccionDTO.class)
                .build();

        return new SynchronizedItemStreamReaderBuilder<TransaccionDTO>()
                .delegate(delegate)
                .build();
    }

    @Bean
    public ItemProcessor<TransaccionDTO, TransaccionEntity> transaccionProcessor() {
        return new TransaccionProcessor();
    }

    @Bean
    public ItemWriter<TransaccionEntity> transaccionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionEntity>()
                .dataSource(dataSource)
                .sql("INSERT INTO transacciones_diarias (id, fecha, monto, tipo, estado) VALUES (:id, :fecha, :monto, :tipo, :estado)")
                .beanMapped()
                .build();
    }

    @Bean
    @SuppressWarnings({"removal", "deprecation"})
    public Step transaccionStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                ItemReader<TransaccionDTO> lector,
                                ItemProcessor<TransaccionDTO, TransaccionEntity> procesador,
                                ItemWriter<TransaccionEntity> escritor) {

        return new StepBuilder("transaccionStep", jobRepository)
                .<TransaccionDTO, TransaccionEntity>chunk(new BancoChunkCompletionPolicy(5, 2000), transactionManager)
                .reader(lector)
                .processor(procesador)
                .writer(escritor)

                .listener(new BancoStepListener())

                .faultTolerant()
                .skipPolicy(new BancoSkipPolicy())
                .retryPolicy(new BancoRetryPolicy())

                .listener(new BancoSkipListener())
                .build();
    }

    @Bean
    public Job reporteTransaccionesJob(JobRepository jobRepository, Step transaccionStep) {
        return new JobBuilder("reporteTransaccionesJob", jobRepository)
                .listener(new BancoJobListener())
                .start(transaccionStep)
                .build();
    }
}
