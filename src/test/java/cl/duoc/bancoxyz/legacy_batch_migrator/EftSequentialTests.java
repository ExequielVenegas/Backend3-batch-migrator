package cl.duoc.bancoxyz.legacy_batch_migrator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:eft_serial;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "eft.workers=1", "eft.chunk-size=2"})
@ActiveProfiles("eft")
@Import(EftBatchAssertions.Faults.class)
class EftSequentialTests extends EftBatchAssertions { }
