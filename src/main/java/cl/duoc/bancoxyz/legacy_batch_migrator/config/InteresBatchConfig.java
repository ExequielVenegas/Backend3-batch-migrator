package cl.duoc.bancoxyz.legacy_batch_migrator.config;

import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoJobListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoSkipListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.listeners.BancoStepListener;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.InteresDTO;
import cl.duoc.bancoxyz.legacy_batch_migrator.model.InteresEntity;
import cl.duoc.bancoxyz.legacy_batch_migrator.processor.InteresProcessor;
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

@org.springframework.context.annotation.Profile("!eft")
@Configuration
public class InteresBatchConfig {

    @Bean
    public ItemReader<InteresDTO> interesReader(@Value("${batch.input.intereses:classpath:input/intereses.csv}") Resource input) {
        var delegate = new FlatFileItemReaderBuilder<InteresDTO>()
                .name("interesReader")
                .resource(input)
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .targetType(InteresDTO.class)
                .build();

        return new SynchronizedItemStreamReaderBuilder<InteresDTO>()
                .delegate(delegate)
                .build();
    }

    @Bean
    public ItemProcessor<InteresDTO, InteresEntity> interesProcessor() {
        return new InteresProcessor();
    }

    @Bean
    public ItemWriter<InteresEntity> interesWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InteresEntity>()
                .dataSource(dataSource)
                .sql("INSERT INTO calculo_intereses (cuenta_id, nombre, tipo, saldo_original, interes_aplicado, saldo_final) " +
                        "VALUES (:cuentaId, :nombre, :tipo, :saldoOriginal, :interesAplicado, :saldoFinal) " +
                        "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), tipo = VALUES(tipo), " +
                        "saldo_original = VALUES(saldo_original), interes_aplicado = VALUES(interes_aplicado), " +
                        "saldo_final = VALUES(saldo_final)")
                .beanMapped()
                .assertUpdates(false)
                .build();
    }

    @Bean
    @SuppressWarnings({"removal", "deprecation"})
    public Step interesStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager,
                            ItemReader<InteresDTO> lector,
                            ItemProcessor<InteresDTO, InteresEntity> procesador,
                            ItemWriter<InteresEntity> escritor) {
        return new StepBuilder("interesStep", jobRepository)
                .<InteresDTO, InteresEntity>chunk(new BancoChunkCompletionPolicy(5, 2000), transactionManager)
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
    public Job calculoInteresesJob(JobRepository jobRepository, Step interesStep) {
        return new JobBuilder("calculoInteresesJob", jobRepository)
                .listener(new BancoJobListener())
                .start(interesStep)
                .build();
    }
}

