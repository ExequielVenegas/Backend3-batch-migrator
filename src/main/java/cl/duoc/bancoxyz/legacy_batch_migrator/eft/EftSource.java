package cl.duoc.bancoxyz.legacy_batch_migrator.eft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;

/** Input inspection is deterministic and does not write to business tables. */
public record EftSource(Path path, String hash, int rows, Set<Integer> repeatedAccounts) {
    public static EftSource inspect(String file, String expectedHash, String kind) {
        try {
            Path path = Path.of(file).toAbsolutePath().normalize();
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = new DigestInputStream(Files.newInputStream(path), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!hash.equals(expectedHash)) throw new IllegalArgumentException("SOURCE_HASH_MISMATCH");
            String header = switch (kind) {
                case "transactions" -> "id,fecha,monto,tipo";
                case "interest" -> "cuenta_id,nombre,saldo,edad,tipo";
                case "annual" -> "cuenta_id,fecha,transaccion,monto,descripcion";
                default -> throw new IllegalArgumentException("UNKNOWN_KIND");
            };
            int rows = 0;
            Set<Integer> seen = new HashSet<>(), repeated = new HashSet<>();
            var tokenizer = new DelimitedLineTokenizer();
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!header.equals(reader.readLine())) throw new IllegalArgumentException("INVALID_HEADER");
                String line;
                while ((line = reader.readLine()) != null) {
                    rows++;
                    if (kind.equals("interest")) {
                        try {
                            int account = Integer.parseInt(tokenizer.tokenize(line).readString(0).trim());
                            if (!seen.add(account)) repeated.add(account);
                        } catch (IllegalArgumentException ignored) { /* Row validator records the rejection. */ }
                    }
                }
            }
            if (rows == 0) throw new IllegalArgumentException("EMPTY_SOURCE");
            return new EftSource(path, hash, rows, Set.copyOf(repeated));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot inspect EFT input", e);
        }
    }
}
