package com.min.edu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "order-access-token.secret=test-order-access-token-secret-32bytes")
class BeApplicationTests {

    @Test
    void contextLoads() {
    }

}
