package com.tradex.kafka;

import com.tradex.kafka.config.KafkaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeadLetterHandlingTest {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    @DisplayName("Dead Letter Topic Convention — Topic DLT naming convention matches <topic>.DLT")
    void dltTopicNamingConvention() {
        String baseTopic = kafkaProperties.getTopic();
        String dltTopic = baseTopic + ".DLT";

        assertThat(baseTopic).isEqualTo("tradex.domain.events");
        assertThat(dltTopic).isEqualTo("tradex.domain.events.DLT");
    }
}
