package com.kitchensink.config;



import com.kitchensink.observability.MdcTaskDecorator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Bounded pool for import batches. Fixed size (= max parallel DB writers), bounded queue, and
     * CallerRuns back-pressure so a flood of imports slows the requester instead of exhausting memory.
     */
    @Bean(name = "importExecutor")
    ThreadPoolTaskExecutor importExecutor(AppProperties props) {
        int n = props.bulkImport().parallelism();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(n);
        ex.setMaxPoolSize(n);
        ex.setQueueCapacity(n * 4);
        ex.setThreadNamePrefix("import-");
        ex.setTaskDecorator(new MdcTaskDecorator());
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        return ex;
    }

    @Bean
    MeterBinder importExecutorMetrics(ThreadPoolTaskExecutor importExecutor) {
        return registry -> {
            Gauge.builder("members.import.pool.active", importExecutor, ThreadPoolTaskExecutor::getActiveCount)
                    .description("Import worker threads currently running a batch")
                    .register(registry);
            Gauge.builder("members.import.pool.size", importExecutor, ThreadPoolTaskExecutor::getPoolSize)
                    .description("Import worker threads in the pool")
                    .register(registry);
        };
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Kitchensink Members API").version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
