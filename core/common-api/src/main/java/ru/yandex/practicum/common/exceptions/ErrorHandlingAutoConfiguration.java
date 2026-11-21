package ru.yandex.practicum.common.exceptions;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorHandler.class)
    public ErrorHandler errorHandler() {
        return new ErrorHandler();
    }
}
