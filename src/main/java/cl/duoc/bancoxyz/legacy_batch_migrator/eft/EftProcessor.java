package cl.duoc.bancoxyz.legacy_batch_migrator.eft;

import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;

public class EftProcessor implements ItemProcessor<EftProcessor.Row, EftRecord> {
    public record Row(int line, String raw) { }
    private final String kind, hash, period;
    private final Set<Integer> repeated;
    private static final Map<String, BigDecimal> MONTHLY_RATES = Map.of(
            "ahorro", new BigDecimal("0.03"), "prestamo", new BigDecimal("0.05"),
            "hipoteca", new BigDecimal("0.04"));
    public EftProcessor(String kind, String hash, String period, Set<Integer> repeated) {
        this.kind = kind; this.hash = hash; this.period = period; this.repeated = repeated;
    }
    @Override public EftRecord process(Row row) {
        try {
            var fields = new DelimitedLineTokenizer().tokenize(row.raw());
            if (fields.getFieldCount() != (kind.equals("transactions") ? 4 : 5)) fail("INVALID_COLUMNS");
            int id = Integer.parseInt(fields.readString(0).trim());
            if (id <= 0) fail("INVALID_ID");
            if (kind.equals("interest")) {
                if (fields.readString(1).isBlank()) fail("MISSING_NAME");
                BigDecimal saldo = money(fields.readString(2), true);
                int age = Integer.parseInt(fields.readString(3).trim());
                if (age < 18 || age > 120) fail("INVALID_AGE");
                String type = normalize(fields.readString(4));
                BigDecimal rate = MONTHLY_RATES.get(type);
                if (rate == null) fail("INVALID_ACCOUNT_TYPE");
                BigDecimal interest = saldo.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal balance = money(saldo.add(interest).toPlainString(), true);
                boolean conflict = repeated.contains(id);
                return record(row, conflict ? "PENDING_RECONCILIATION" : "VALID",
                        conflict ? "REPEATED_ACCOUNT" : "OK", id, null, type, saldo, interest, balance);
            }
            LocalDate date = date(fields.readString(1));
            String type = normalize(fields.readString(kind.equals("annual") ? 2 : 3));
            BigDecimal amount = money(fields.readString(kind.equals("annual") ? 3 : 2), false);
            if (kind.equals("transactions") && !Set.of("credito", "debito").contains(type)) fail("INVALID_TYPE");
            if (kind.equals("annual")) {
                if (!Set.of("deposito", "retiro", "compra", "pago", "transferencia").contains(type)) fail("INVALID_TYPE");
                if (fields.readString(4).isBlank()) fail("MISSING_DESCRIPTION");
            }
            return record(row, "VALID", "OK", id, date, type, amount, null, null);
        } catch (IllegalArgumentException | ArithmeticException e) {
            String reason = e.getMessage();
            if (reason == null || !reason.matches("[A-Z_]+")) reason = "INVALID_FORMAT";
            return record(row, "REJECTED", reason, null, null, null, null, null, null);
        }
    }
    private EftRecord record(Row row, String status, String reason, Integer id, LocalDate date,
            String type, BigDecimal amount, BigDecimal interest, BigDecimal balance) {
        return new EftRecord(kind, hash, row.line(), period, status, reason, id, date, type,
                amount, interest, balance, row.raw(), Thread.currentThread().getName());
    }
    private static String normalize(String s) { return s.trim().toLowerCase(Locale.ROOT); }
    private static void fail(String reason) { throw new IllegalArgumentException(reason); }
    private static BigDecimal money(String text, boolean zeroAllowed) {
        BigDecimal value = new BigDecimal(text.trim()).setScale(2, RoundingMode.UNNECESSARY);
        if (value.signum() < 0 || (!zeroAllowed && value.signum() == 0)
                || value.precision() > 19) fail("INVALID_AMOUNT");
        return value;
    }
    private static LocalDate date(String text) {
        for (String pattern : List.of("uuuu-MM-dd", "dd-MM-uuuu", "uuuu/MM/dd", "dd/MM/uuuu")) {
            try { return LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT)); }
            catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("INVALID_DATE");
    }
}

