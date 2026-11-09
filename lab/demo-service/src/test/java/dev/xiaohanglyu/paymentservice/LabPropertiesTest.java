package dev.xiaohanglyu.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import dev.xiaohanglyu.paymentservice.LabProperties.Fault;

class LabPropertiesTest {

    @Test
    void bindsKebabCaseFaultNamesFromAnEnvironmentVariable() {
        LabProperties lab = bind(Map.of("lab.version", "v1.4.0",
                "lab.faults", "slow-query,connection-leak"));

        assertThat(lab.version()).isEqualTo("v1.4.0");
        assertThat(lab.faults()).containsExactlyInAnyOrder(Fault.SLOW_QUERY, Fault.CONNECTION_LEAK);
    }

    @Test
    void anEmptyFaultListIsTheHealthyBaseline() {
        LabProperties lab = bind(Map.of("lab.faults", ""));

        assertThat(lab.faults()).isEmpty();
        assertThat(lab.version()).isEqualTo("dev");
    }

    private static LabProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("lab", LabProperties.class);
    }
}
