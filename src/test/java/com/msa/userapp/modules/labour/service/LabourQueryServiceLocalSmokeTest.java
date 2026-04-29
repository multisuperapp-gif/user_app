package com.msa.userapp.modules.labour.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "app.persistence.mongo.enabled=false"
        }
)
@ActiveProfiles("local")
class LabourQueryServiceLocalSmokeTest {

    @Autowired
    private LabourQueryService labourQueryService;

    @Test
    void landingLoadsAgainstConfiguredDatabase() {
        LabourApiDtos.LabourLandingResponse landing = labourQueryService.landing(null, null, null, null, null, 0, 5);
        assertNotNull(landing);
        assertNotNull(landing.categories());
        PageResponse<LabourApiDtos.LabourProfileCardResponse> profiles = landing.profiles();
        assertNotNull(profiles);
        System.out.println("categories=" + landing.categories().size());
        System.out.println("profiles=" + profiles.items().size());
    }
}
