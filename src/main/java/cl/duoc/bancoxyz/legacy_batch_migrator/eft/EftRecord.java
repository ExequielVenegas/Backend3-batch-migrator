package cl.duoc.bancoxyz.legacy_batch_migrator.eft;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EftRecord(String kind, String hash, int line, String period, String status,
        String reason, Integer reference, LocalDate date, String type, BigDecimal amount,
        BigDecimal interest, BigDecimal balance, String raw, String worker) {
    public static final String RULE_VERSION = "monthly-v1";
}
