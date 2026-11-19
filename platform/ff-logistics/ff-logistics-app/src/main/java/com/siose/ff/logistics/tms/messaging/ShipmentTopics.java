package com.siose.ff.logistics.tms.messaging;

import com.siose.ff.logistics.tms.ShipmentDelivered;
import com.siose.ff.logistics.tms.ShipmentProgressed;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The Kafka topics TMS owns. Declared only when events are externalized, so an app without a
 * broker does not reach for one at its start. Spring's KafkaAdmin creates a missing topic when the
 * app starts and never shrinks an existing one; the broker creates none by itself. Not in the
 * {@code migrate} profile: the PreSync Job only migrates and should not wait for a broker.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true")
@Profile("!migrate")
class ShipmentTopics {

    @Bean
    NewTopic shipmentDeliveredTopic(@Value("${logistics.events.kafka.partitions:3}") int partitions,
            @Value("${logistics.events.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentDelivered.TOPIC).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic shipmentProgressedTopic(@Value("${logistics.events.kafka.partitions:3}") int partitions,
            @Value("${logistics.events.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentProgressed.TOPIC).partitions(partitions).replicas(replicas).build();
    }
}
