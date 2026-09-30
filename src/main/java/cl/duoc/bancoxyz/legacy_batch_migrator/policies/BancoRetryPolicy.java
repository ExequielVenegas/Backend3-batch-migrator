package cl.duoc.bancoxyz.legacy_batch_migrator.policies;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BancoRetryPolicy extends SimpleRetryPolicy {

    public BancoRetryPolicy() {
        super(3, buildRetryableExceptions());
    }

    private static Map<Class<? extends Throwable>, Boolean> buildRetryableExceptions() {
        Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
        map.put(TransientDataAccessException.class, true);

        return map;
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        super.registerThrowable(context, throwable);

        if (canRetry(context)) {
            log.warn("RetryPolicy activada: Reintento {}/{} debido a error transitorio: {}",
                    context.getRetryCount(),
                    getMaxAttempts(),
                    throwable.getMessage());
        }
    }
}
