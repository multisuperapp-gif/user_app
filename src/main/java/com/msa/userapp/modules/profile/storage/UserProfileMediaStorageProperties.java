package com.msa.userapp.modules.profile.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class UserProfileMediaStorageProperties {
    private String provider = "S3";
    private String localRoot = System.getProperty("java.io.tmpdir") + "/msa-storage";
    private String awsRegion = "us-east-1";
    private final PublicMedia publicMedia = new PublicMedia();
    private final Prefixes prefixes = new Prefixes();

    @Getter
    @Setter
    public static class PublicMedia {
        private String bucket = "multisuperapp-bucket";
    }

    @Getter
    @Setter
    public static class Prefixes {
        private String profile = "msa-public-media-dev/profiles/user";
    }
}
