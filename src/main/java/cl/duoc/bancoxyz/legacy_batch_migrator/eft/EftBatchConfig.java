package cl.duoc.bancoxyz.legacy_batch_migrator.eft;

import java.time.YearMonth;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.separator.SimpleRecordSeparatorPolicy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import cl.duoc.bancoxyz.legacy_batch_migrator.policies.BancoRetryPolicy;

@Configuration
@Profile("eft")
public class EftBatchConfig {
    @Bean @StepScope
    public Partitioner eftPartitioner(@Value("#{jobParameters['input']}") String input,
            @Value("#{jobParameters['sha256']}") String hash,
            @Value("#{jobParameters['kind']}") String kind) {
        return grid -> {
            var source = EftSource.inspect(input, hash, kind);
            Map<String, ExecutionContext> result = new LinkedHashMap<>();
            int size = Math.max(1, (source.rows() + grid - 1) / grid);
            for (int start = 0, partition = 0; start < source.rows(); start += size, partition++) {
                var context = new ExecutionContext();
                context.putInt("start", start);
                context.putInt("end", Math.min(start + size, source.rows()));
                result.put("p" + partition, context);
            }
            return result;
        };
    }
    @Bean @StepScope
    public FlatFileItemReader<EftProcessor.Row> eftReader(
            @Value("#{jobParameters['input']}") String input,
            @Value("#{jobParameters['sha256']}") String hash,
            @Value("#{jobParameters['kind']}") String kind,
            @Value("#{stepExecutionContext['start']}") int start,
            @Value("#{stepExecutionContext['end']}") int end) {
        var source = EftSource.inspect(input, hash, kind);
        return new FlatFileItemReaderBuilder<EftProcessor.Row>().name("eftReader")
                .resource(new FileSystemResource(source.path())).encoding("UTF-8")
                .linesToSkip(start + 1).maxItemCount(end - start).comments(new String[0])
                .recordSeparatorPolicy(new SimpleRecordSeparatorPolicy())
                .lineMapper((line, number) -> new EftProcessor.Row(number, line)).build();
    }
    @Bean @StepScope
    public EftProcessor eftProcessor(@Value("#{jobParameters['input']}") String input,
            @Value("#{jobParameters['sha256']}") String hash,
            @Value("#{jobParameters['kind']}") String kind,
            @Value("#{jobParameters['period']}") String period) {
        return new EftProcessor(kind, hash, period, EftSource.inspect(input, hash, kind).repeatedAccounts());
    }
    @Bean
    public ItemWriter<EftRecord> eftWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<EftRecord>().dataSource(dataSource)
                .sql("INSERT INTO eft_records (kind,source_hash,source_line,period,rule_version,status,reason,"
                        + "reference_id,operation_date,operation_type,amount,interest,final_balance,raw_line,worker) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_line=VALUES(source_line)")
                .itemPreparedStatementSetter((r, ps) -> {
                    ps.setString(1,r.kind()); ps.setString(2,r.hash()); ps.setInt(3,r.line());
                    ps.setString(4,r.period()); ps.setString(5,EftRecord.RULE_VERSION);
                    ps.setString(6,r.status()); ps.setString(7,r.reason()); ps.setObject(8,r.reference());
                    ps.setObject(9,r.date()); ps.setString(10,r.type()); ps.setBigDecimal(11,r.amount());
                    ps.setBigDecimal(12,r.interest()); ps.setBigDecimal(13,r.balance());
                    ps.setString(14,r.raw()); ps.setString(15,r.worker());
                }).assertUpdates(false).build();
    }
    @Bean @SuppressWarnings({"removal", "deprecation"})
    public Step eftWorker(JobRepository repository, PlatformTransactionManager tx,
            FlatFileItemReader<EftProcessor.Row> eftReader, EftProcessor eftProcessor,
            @Qualifier("eftWriter") ItemWriter<EftRecord> writer,
            @Value("${eft.chunk-size:100}") int chunk) {
        if (chunk < 1 || chunk > 10000) throw new IllegalArgumentException("Invalid EFT chunk size");
        return new StepBuilder("eftWorker", repository).<EftProcessor.Row,EftRecord>chunk(chunk,tx)
                .reader(eftReader).processor(eftProcessor).writer(writer)
                .faultTolerant().retryPolicy(new BancoRetryPolicy()).build();
    }
    @Bean
    public Step eftManager(JobRepository repository, @Qualifier("eftWorker") Step worker,
            Partitioner eftPartitioner, TaskExecutor taskExecutor,
            @Value("${eft.workers:3}") int workers) {
        if (workers < 1 || workers > 8) throw new IllegalArgumentException("EFT workers must be 1..8");
        return new StepBuilder("eftManager",repository).partitioner("eftWorker",eftPartitioner)
                .step(worker).gridSize(workers).taskExecutor(taskExecutor).build();
    }
    @Bean public Job eftTransaccionesJob(JobRepository r, @Qualifier("eftManager") Step s) {
        return job(r,s,"eftTransaccionesJob","transactions");
    }
    @Bean public Job eftInteresesMensualesJob(JobRepository r, @Qualifier("eftManager") Step s) {
        return job(r,s,"eftInteresesMensualesJob","interest");
    }
    @Bean public Job eftEstadosAnualesJob(JobRepository r, @Qualifier("eftManager") Step s) {
        return job(r,s,"eftEstadosAnualesJob","annual");
    }
    private Job job(JobRepository repository, Step step, String name, String kind) {
        return new JobBuilder(name,repository).validator(parameters -> {
            try {
                if (!kind.equals(parameters.getString("kind"))) throw new IllegalArgumentException();
                YearMonth.parse(parameters.getString("period"));
                if (!EftRecord.RULE_VERSION.equals(parameters.getString("rules"))) throw new IllegalArgumentException();
                EftSource.inspect(parameters.getString("input"), parameters.getString("sha256"), kind);
            } catch (RuntimeException e) {
                throw new InvalidJobParametersException("Invalid EFT parameters or input: " + e.getClass().getSimpleName());
            }
        }).start(step).build();
    }
}
