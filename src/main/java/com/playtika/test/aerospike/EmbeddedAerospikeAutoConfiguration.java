/*
* The MIT License (MIT)
*
* Copyright (c) 2017 Playtika
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
 */
package com.playtika.test.aerospike;

import com.aerospike.client.AerospikeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HostPortWaitStrategy;
import org.testcontainers.containers.wait.WaitAllStrategy;
import org.testcontainers.containers.wait.WaitStrategy;

import java.time.Duration;
import java.util.LinkedHashMap;

import static com.playtika.test.aerospike.AerospikeProperties.AEROSPIKE_BEAN_NAME;
import static com.playtika.test.common.utils.ContainerUtils.containerLogsConsumer;
import static java.time.temporal.ChronoUnit.SECONDS;

@Slf4j
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(value = "embedded.aerospike.enabled", matchIfMissing = true)
@ConditionalOnClass(AerospikeClient.class)
@EnableConfigurationProperties(AerospikeProperties.class)
public class EmbeddedAerospikeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AerospikeWaitStrategy aerospikeStartupCheckStrategy(AerospikeProperties properties) {
        return new AerospikeWaitStrategy(properties);
    }

    @Bean(name = AEROSPIKE_BEAN_NAME, destroyMethod = "stop")
    public GenericContainer aerosike(AerospikeWaitStrategy aerospikeWaitStrategy,
                                     ConfigurableEnvironment environment,
                                     AerospikeProperties properties) {
        log.info("Starting aerospike server. Docker image: {}", properties.dockerImage);
        WaitStrategy waitStrategy = new WaitAllStrategy()
                .withStrategy(aerospikeWaitStrategy)
                .withStrategy(new HostPortWaitStrategy())
                .withStartupTimeout(Duration.of(60, SECONDS));

        GenericContainer aerospike =
                new GenericContainer<>(properties.dockerImage)
                        .withExposedPorts(properties.port)
                        .withLogConsumer(containerLogsConsumer(log))
                        // see https://github.com/aerospike/aerospike-server.docker/blob/master/aerospike.template.conf
                        .withEnv("NAMESPACE", properties.namespace)
                        .withEnv("SERVICE_PORT", String.valueOf(properties.port))
                        .withEnv("MEM_GB", String.valueOf(1))
                        .withEnv("STORAGE_GB", String.valueOf(1))
                        .waitingFor(waitStrategy);

        aerospike.start();
        registerAerospikeEnvironment(aerospike, environment, properties);
        return aerospike;
    }

    private void registerAerospikeEnvironment(GenericContainer aerosike,
                                              ConfigurableEnvironment environment,
                                              AerospikeProperties properties) {
        Integer mappedPort = aerosike.getMappedPort(properties.port);
        String host = aerosike.getContainerIpAddress();

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.aerospike.host", host);
        map.put("embedded.aerospike.port", mappedPort);
        map.put("embedded.aerospike.namespace", properties.namespace);

        log.info("Started aerospike server. Connection details {}", map);

        MapPropertySource propertySource = new MapPropertySource("embeddedAerospikeInfo", map);
        environment.getPropertySources().addFirst(propertySource);
    }
}
