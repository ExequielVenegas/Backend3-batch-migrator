package cl.duoc.bancoxyz.legacy_batch_migrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfig {

    @Value("${bancoxyz.task-executor.core-pool-size}")
    private int corePoolSize;

    @Value("${bancoxyz.task-executor.max-pool-size}")
    private int maxPoolSize;

    @Value("${bancoxyz.task-executor.queue-capacity}")
    private int queueCapacity;

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);

        executor.setThreadNamePrefix("Batch-Thread-");
        executor.initialize();

        return executor;
    }
}