package com.kitchensink;




import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class KitchensinkApplicationTest {

    @Test
    void mainStartsTheSpringApplication() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            String[] args = {"--x=1"};

            KitchensinkApplication.main(args);

            spring.verify(() -> SpringApplication.run(KitchensinkApplication.class, args));
        }
    }
}
