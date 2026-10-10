package cl.duoc.bancoxyz.legacy_batch_migrator.eft;

import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component @Profile("eft") @ConditionalOnProperty(name="eft.launch",havingValue="true")
public class EftLauncher implements ApplicationRunner, ExitCodeGenerator {
    private static final Logger log=LoggerFactory.getLogger(EftLauncher.class);
    private final JobLauncher launcher;
    private final Map<String,Job> jobs;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private int exitCode=1;
    public EftLauncher(JobLauncher launcher, Map<String,Job> jobs, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.launcher=launcher; this.jobs=jobs; this.jdbc=jdbc;
    }
    @Override public int getExitCode() { return exitCode; }
    @Override public void run(ApplicationArguments args) throws Exception {
        // Fail before creating a JobInstance if schema initialization was incomplete.
        for(String table:java.util.List.of("BATCH_JOB_INSTANCE","BATCH_JOB_EXECUTION","BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_STEP_EXECUTION","BATCH_STEP_EXECUTION_CONTEXT","BATCH_JOB_EXECUTION_CONTEXT",
                "eft_records")) {
            jdbc.queryForList("SELECT 1 FROM " + table + " WHERE 1=0");
        }
        String kind=option(args,"eft.kind");
        String name=switch(kind) {
            case "transactions" -> "eftTransaccionesJob";
            case "interest" -> "eftInteresesMensualesJob";
            case "annual" -> "eftEstadosAnualesJob";
            default -> throw new IllegalArgumentException("Unknown EFT kind");
        };
        int attempts=args.containsOption("eft.attempts") ? Integer.parseInt(option(args,"eft.attempts")) : 3;
        if (attempts<1 || attempts>3) throw new IllegalArgumentException("EFT attempts must be 1..3");
        var parameters=new JobParametersBuilder().addString("input",option(args,"eft.input"),false)
                .addString("sha256",option(args,"eft.sha256")).addString("period",option(args,"eft.period"))
                .addString("kind",kind).addString("rules",EftRecord.RULE_VERSION).toJobParameters();
        for(int attempt=1;attempt<=attempts;attempt++) {
            try {
                var execution=launcher.run(jobs.get(name),parameters);
                log.info("EFT job={} execution={} attempt={} status={}",name,execution.getId(),attempt,execution.getStatus());
                if(execution.getStatus()==BatchStatus.COMPLETED) { exitCode=0; return; }
                if(execution.getStatus()!=BatchStatus.FAILED) return;
                if(attempt<attempts) Thread.sleep(1000L * attempt);
            } catch(JobInstanceAlreadyCompleteException done) {
                log.info("EFT source/period/rules already completed; no new load");
                exitCode=0; return;
            }
        }
    }
    private static String option(ApplicationArguments args,String name) {
        var values=args.getOptionValues(name);
        if(values==null || values.size()!=1 || values.getFirst().isBlank())
            throw new IllegalArgumentException("Missing or repeated argument: "+name);
        return values.getFirst();
    }
}

