package com.kalin.investment_tracker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ExternalHttpConfig {
    private final ExternalHttpProperties properties;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        RestTemplate template = new RestTemplate(factory);
        template.setInterceptors(List.of((request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getHeaders().getContentLength() > properties.getMaximumResponseBytes()) {
                response.close();
                throw new IOException("External response exceeded the configured size limit");
            }
            return new ClientHttpResponse() {
                private InputStream boundedBody;
                @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
                @Override public String getStatusText() throws IOException { return response.getStatusText(); }
                @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
                @Override public void close() { response.close(); }
                @Override public InputStream getBody() throws IOException {
                    if (boundedBody == null) boundedBody = new BoundedInputStream(response.getBody(), properties.getMaximumResponseBytes());
                    return boundedBody;
                }
            };
        }));
        return template;
    }
}
