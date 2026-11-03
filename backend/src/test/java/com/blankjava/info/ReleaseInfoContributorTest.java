package com.blankjava.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.info.BuildProperties;

class ReleaseInfoContributorTest {

    @Test
    void exposesTheReleaseAndTheContractVersion() {
        Properties build = new Properties();
        build.setProperty("version", "0.1.0");
        build.setProperty("api", "0.1.0");

        Info info = contribute("0.1.0-b", new BuildProperties(build));

        assertThat(info.getDetails()).containsEntry("release", "0.1.0-b").containsEntry("api", "0.1.0");
    }

    @Test
    void reportsUnknownWhenBuildInfoIsMissing() {
        Info info = contribute("0.1.0-b", null);

        assertThat(info.getDetails()).containsEntry("api", ReleaseInfoContributor.UNKNOWN);
    }

    @Test
    void reportsUnknownWhenBuildInfoHasNoContractVersion() {
        Info info = contribute("0.1.0-b", new BuildProperties(new Properties()));

        assertThat(info.getDetails()).containsEntry("api", ReleaseInfoContributor.UNKNOWN);
    }

    @SuppressWarnings("unchecked")
    private static Info contribute(String release, BuildProperties buildProperties) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(buildProperties);
        Info.Builder builder = new Info.Builder();
        new ReleaseInfoContributor(release, provider).contribute(builder);
        return builder.build();
    }
}
