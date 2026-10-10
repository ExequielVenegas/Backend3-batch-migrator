package cl.duoc.bancoxyz.legacy_batch_migrator;

import cl.duoc.bancoxyz.legacy_batch_migrator.eft.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.*;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.TransientDataAccessResourceException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:eft_parallel;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "eft.workers=3", "eft.chunk-size=2"})
@ActiveProfiles("eft")
@Import(EftBatchTests.Faults.class)
class EftBatchTests extends EftBatchAssertions { }

abstract class EftBatchAssertions {
    private static final Map<String,List<Map<String,Object>>> RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    @Autowired JobLauncher launcher;
    @Autowired Map<String,Job> jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired FaultControl fault;
    @Autowired org.springframework.core.env.Environment environment;
    static class FaultControl {
        AtomicBoolean critical = new AtomicBoolean();
        AtomicInteger transientErrors = new AtomicInteger();
    }
    @TestConfiguration static class Faults {
        @Bean static FaultControl faultControl() { return new FaultControl(); }
        @Bean static BeanPostProcessor injectFault(FaultControl fault) {
            return new BeanPostProcessor() {
                @Override @SuppressWarnings("unchecked")
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!name.equals("eftWriter")) return bean;
                    var delegate = (ItemWriter<EftRecord>) bean;
                    return (ItemWriter<EftRecord>) chunk -> {
                        if (fault.critical.get() && chunk.getItems().stream().anyMatch(r -> r.line() >= 4))
                            throw new IllegalStateException("Injected critical writer failure");
                        if (fault.transientErrors.getAndUpdate(v -> Math.max(0,v-1)) > 0)
                            throw new TransientDataAccessResourceException("Injected temporary database failure");
                        delegate.write(chunk);
                    };
                }
            };
        }
    }
    JobParameters parameters(String kind, String file, String period) throws Exception {
        Path path = Path.of(file).toAbsolutePath();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        return new JobParametersBuilder().addString("input",path.toString(),false)
                .addString("sha256",hash).addString("kind",kind).addString("period",period)
                .addString("rules",EftRecord.RULE_VERSION).toJobParameters();
    }
    Job job(String kind) { return jobs.get(switch(kind) {
        case "interest" -> "eftInteresesMensualesJob";
        case "annual" -> "eftEstadosAnualesJob";
        default -> "eftTransaccionesJob";
    }); }
    long count(String period, String status) {
        return jdbc.queryForObject("select count(*) from eft_records where period=? and status=?",Long.class,period,status);
    }
    @AfterEach void resetFault() { fault.critical.set(false); fault.transientErrors.set(0); }
    @Test void monthlyCalculationAndReconciliation() throws Exception {
        var p=parameters("interest","src/test/resources/eft/interest.csv","2026-01");
        var e=launcher.run(job("interest"),p);
        assertEquals(BatchStatus.COMPLETED,e.getStatus(),e.getAllFailureExceptions().toString());
        assertEquals(4,count("2026-01","VALID"));
        assertEquals(2,count("2026-01","PENDING_RECONCILIATION"));
        assertEquals(3,count("2026-01","REJECTED"));
        assertEquals(new java.math.BigDecimal("250.02"),jdbc.queryForObject(
                "select sum(interest) from eft_records where period='2026-01' and status='VALID'",java.math.BigDecimal.class));
        assertThrows(JobInstanceAlreadyCompleteException.class,()->launcher.run(job("interest"),p));
    }
    @Test void strictDatesAndHistoricalMovements() throws Exception {
        for (String kind:List.of("transactions","annual")) {
            var e=launcher.run(job(kind),parameters(kind,"src/test/resources/eft/"+kind+".csv","2026-02"));
            assertEquals(BatchStatus.COMPLETED,e.getStatus(),e.getAllFailureExceptions().toString());
            assertEquals(2L,jdbc.queryForObject("select count(*) from eft_records where period='2026-02' and kind=? and status='VALID'",Long.class,kind));
            assertEquals(4L,jdbc.queryForObject("select count(*) from eft_records where period='2026-02' and kind=? and status='REJECTED'",Long.class,kind));
        }
    }
    @Test void restartsFailedInstanceWithoutDuplicatingCommittedRows() throws Exception {
        var p=parameters("transactions","src/test/resources/eft/transactions.csv","2026-03");
        fault.critical.set(true);
        var failed=launcher.run(job("transactions"),p);
        assertEquals(BatchStatus.FAILED,failed.getStatus());
        assertTrue(jdbc.queryForObject("select count(*) from eft_records where period='2026-03'",Long.class)>0);
        fault.critical.set(false);
        var recovered=launcher.run(job("transactions"),p);
        assertEquals(failed.getJobInstance().getInstanceId(),recovered.getJobInstance().getInstanceId());
        assertEquals(BatchStatus.COMPLETED,recovered.getStatus(),recovered.getAllFailureExceptions().toString());
        assertEquals(6L,jdbc.queryForObject("select count(*) from eft_records where period='2026-03'",Long.class));
    }
    @Test void retriesTemporaryWriterFailure() throws Exception {
        fault.transientErrors.set(2);
        var e=launcher.run(job("transactions"),parameters("transactions","src/test/resources/eft/transactions.csv","2026-04"));
        assertEquals(BatchStatus.COMPLETED,e.getStatus(),e.getAllFailureExceptions().toString());
        assertEquals(6L,jdbc.queryForObject("select count(*) from eft_records where period='2026-04'",Long.class));
    }
    @Test void processesAllSuppliedRowsWithoutWritingLegacy() throws Exception {
        var files=Map.of("transactions","movimientos_financieros_diarios.csv","interest","intereses_trimestrales.csv","annual","estados_financieros_anuales.csv");
        for(var entry:files.entrySet()) {
            var p=parameters(entry.getKey(),"src/main/resources/input/"+entry.getValue(),"2026-05");
            var e=launcher.run(job(entry.getKey()),p);
            assertEquals(BatchStatus.COMPLETED,e.getStatus(),e.getAllFailureExceptions().toString());
            long expected;
            try(var lines=Files.lines(Path.of(p.getString("input")))) { expected=lines.count()-1; }
            assertEquals(expected,jdbc.queryForObject("select count(*) from eft_records where kind=? and period='2026-05'",Long.class,entry.getKey()));
            assertEquals(expected,jdbc.queryForObject("select count(distinct source_line) from eft_records where kind=? and period='2026-05'",Long.class,entry.getKey()));
            assertTrue(e.getStepExecutions().stream().anyMatch(s->s.getStepName().startsWith("eftWorker:p")));
            var normalized=jdbc.queryForList("select source_line,status,reason,reference_id,operation_date,operation_type,amount,interest,final_balance,raw_line from eft_records where kind=? and period='2026-05' order by source_line",entry.getKey());
            var previous=RESULTS.putIfAbsent(entry.getKey(),normalized);
            if(previous!=null) assertEquals(previous,normalized,"Sequential/parallel results differ");
            int workers=environment.getProperty("eft.workers",Integer.class,3);
            long used=jdbc.queryForObject("select count(distinct worker) from eft_records where kind=? and period='2026-05'",Long.class,entry.getKey());
            assertEquals(workers,used,"All configured workers must process rows");
            System.out.println("EFT verified " + entry.getKey() + " rows=" + expected + " workers=" + used
                    + " statuses=" + jdbc.queryForList("select status,count(*) total from eft_records where kind=? and period='2026-05' group by status",entry.getKey()));
        }
        assertEquals(0L,jdbc.queryForObject("select count(*) from information_schema.tables where lower(table_name) in ('transacciones_diarias','calculo_intereses','estado_cuenta_anual')",Long.class));
    }
    @Test void refusesChangedInputAndWrongJobKind() throws Exception {
        var p=parameters("transactions","src/test/resources/eft/transactions.csv","2026-06");
        assertThrows(InvalidJobParametersException.class,()->launcher.run(job("annual"),p));
        var wrong=new JobParametersBuilder(p).addString("sha256","0".repeat(64)).toJobParameters();
        assertThrows(InvalidJobParametersException.class,()->launcher.run(job("transactions"),wrong));
    }
    @Test void automaticRestartIsBoundedAndSameInstanceCanRecover() throws Exception {
        var p=parameters("transactions","src/test/resources/eft/transactions.csv","2026-07");
        var args=new org.springframework.boot.DefaultApplicationArguments("--eft.kind=transactions",
                "--eft.input="+p.getString("input"),"--eft.sha256="+p.getString("sha256"),"--eft.period=2026-07");
        long before=jdbc.queryForObject("select count(*) from BATCH_JOB_EXECUTION",Long.class);
        fault.critical.set(true);
        var runner=new EftLauncher(launcher,jobs,jdbc);
        runner.run(args);
        assertEquals(1,runner.getExitCode());
        assertEquals(before+3,jdbc.queryForObject("select count(*) from BATCH_JOB_EXECUTION",Long.class));
        fault.critical.set(false);
        runner.run(args);
        assertEquals(0,runner.getExitCode());
        runner.run(args);
        assertEquals(before+4,jdbc.queryForObject("select count(*) from BATCH_JOB_EXECUTION",Long.class));
        assertEquals(6L,jdbc.queryForObject("select count(*) from eft_records where period='2026-07'",Long.class));
    }
}

