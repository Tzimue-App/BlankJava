package com.blankjava.info;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * Adds to {@code /actuator/info}, next to {@code build.version} (backend version):
 *
 * <ul>
 *   <li>{@code release}: the project release the image belongs to ({@code APP_RELEASE}, set at
 *       image build, e.g. {@code 0.1.0-b});
 *   <li>{@code api}: the API contract version the backend was generated from.
 * </ul>
 *
 * <p>Shown in the frontend footer and used in bug reports (docs/technical_analysis.md §8.2).
 */
@Component
public class ReleaseInfoContributor implements InfoContributor {

    static final String UNKNOWN = "unknown";

    private final String release;
    private final ObjectProvider<BuildProperties> buildProperties;

    public ReleaseInfoContributor(
            @Value("${blankjava.release:" + UNKNOWN + "}") String release,
            ObjectProvider<BuildProperties> buildProperties) {
        this.release = release;
        this.buildProperties = buildProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        BuildProperties build = buildProperties.getIfAvailable();
        String api = build == null ? null : build.get("api");
        builder.withDetail("release", release).withDetail("api", api == null ? UNKNOWN : api);
    }
}
