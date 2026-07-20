package com.tmp.bootstrap;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SpringContextSmokeTest {

    @Autowired
    private Clock systemClock;

    @Test
    void contextLoads() {
        assertNotNull(systemClock, "Spring composition root must provide infrastructure beans");
    }
}
