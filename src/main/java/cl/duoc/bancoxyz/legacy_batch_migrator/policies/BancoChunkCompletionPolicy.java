package cl.duoc.bancoxyz.legacy_batch_migrator.policies;


import lombok.Getter;
import org.springframework.batch.infrastructure.repeat.CompletionPolicy;
import org.springframework.batch.infrastructure.repeat.RepeatContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.repeat.context.RepeatContextSupport;

public class BancoChunkCompletionPolicy implements CompletionPolicy {

    private final int chunkSize;
    private final long timeoutMillis;

    public BancoChunkCompletionPolicy(int chunkSize, long timeoutMillis) {
        this.chunkSize = chunkSize;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public boolean isComplete(RepeatContext context, RepeatStatus result) {
        return isComplete(context);
    }

    @Override
    public boolean isComplete(RepeatContext context) {
        BancoRepeatContext customContext = (BancoRepeatContext) context;

        boolean isSizeReached = customContext.getStartedCount() >= chunkSize;

        boolean isTimeoutReached = (System.currentTimeMillis() - customContext.getStartTime()) >= timeoutMillis;

        return isSizeReached || isTimeoutReached;
    }

    @Override
    public RepeatContext start(RepeatContext parent) {
        return new BancoRepeatContext(parent);
    }

    @Override
    public void update(RepeatContext context) {
        ((BancoRepeatContext) context).increment();
    }

    @Getter
    private static class BancoRepeatContext extends RepeatContextSupport {
        private int startedCount = 0;
        private final long startTime;

        public BancoRepeatContext(RepeatContext parent) {
            super(parent);
            this.startTime = System.currentTimeMillis();
        }

        public void increment() {
            this.startedCount++;
        }

    }
}