package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CoexParkingStatusServiceTest {

    @Test
    void parsesParkingPredictionAndCurrentStatus() {
        CoexParkingStatusService service =
                new CoexParkingStatusService(Duration.ofSeconds(1), Duration.ofSeconds(1));
        String html = """
                <div class="ParkingApiMapGraphInfo">만차 발생 시간 : <strong>10시</strong></div>
                <div class="ParkingApiMapContInfo"><span class="ParkingApiMapContInfo-title">만차</span></div>
                <script>
                  var chart = { datasets: [{ label: '주차 대수', data:
                    [100,200,300,400,500,600,700,800,900,1000,1100,1200,
                     1300,1400,1500,1600,1700,1800,1900,2000,2100,2200,2300,2400,2500] }] };
                </script>
                """;

        var result = service.parse(html);

        assertThat(result.status()).isEqualTo("FULL");
        assertThat(result.expectedFullHour()).isEqualTo(10);
        assertThat(result.hourlyCounts()).hasSize(24);
        assertThat(result.hourlyCounts().getFirst()).isEqualTo(100);
        assertThat(result.hourlyCounts().getLast()).isEqualTo(2400);
        assertThat(result.stale()).isFalse();
    }
}
