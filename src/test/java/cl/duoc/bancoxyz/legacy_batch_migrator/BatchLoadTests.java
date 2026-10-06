package cl.duoc.bancoxyz.legacy_batch_migrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BatchLoadTests {
    @Autowired JobLauncher launcher;
    @Autowired Map<String, Job> jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void loadsAllCsvAndRefusesCompletedLoadsWithoutDeletingData() throws Exception {
        var tables = Map.of("calculoInteresesJob", "calculo_intereses",
                "reporteTransaccionesJob", "transacciones_diarias",
                "generacionEstadosCuentaJob", "estado_cuenta_anual");
        var parameters = new JobParametersBuilder().addString("load.id", "initial-v1").toJobParameters();
        for (var entry : tables.entrySet()) {
            var job = jobs.get(entry.getKey());
            var execution = launcher.run(job, parameters);
            assertEquals(BatchStatus.COMPLETED, execution.getStatus(), execution.getAllFailureExceptions().toString());
            long written = execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum();
            assertTrue(written > 0);
            long persisted = jdbc.queryForObject("SELECT COUNT(*) FROM " + entry.getValue(), Long.class);
            if (entry.getKey().equals("calculoInteresesJob")) {
                // El CSV repite cuentas: el writer actualiza por cuenta_id.
                assertTrue(persisted > 0 && persisted <= written);
            } else {
                assertEquals(written, persisted);
            }
            assertThrows(JobInstanceAlreadyCompleteException.class, () -> launcher.run(job, parameters));
            assertEquals(persisted, jdbc.queryForObject("SELECT COUNT(*) FROM " + entry.getValue(), Long.class));
        }
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM estado_cuenta_anual", Long.class);
        new ResourceDatabasePopulator(new ClassPathResource("script/estadocuentaanual.sql"),
                new ClassPathResource("script/calculoInteres.sql"),
                new ClassPathResource("script/transacciones.sql")).execute(dataSource);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM estado_cuenta_anual", Long.class));
    }
}
