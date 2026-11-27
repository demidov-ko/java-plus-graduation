package evm.stat.client.config;

import evm.stat.client.AnalyzerClient;
import evm.stat.client.CollectorClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({CollectorClient.class, AnalyzerClient.class})
public class StatsGrpcClientAutoConfiguration {
}
