package nebula.plugin.metrics.model;

import lombok.Value;
import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Gradle start parameters.
 */
@Value
public class GradleParameters {
    public static GradleParameters fromGradle(org.gradle.api.invocation.Gradle gradle) {
        StartParameter start = gradle.getStartParameter();
        List<KeyValue> projectProperties = KeyValue.mapToKeyValueList(start.getProjectProperties());
        return new GradleParameters(start.getTaskRequests(),
                start.getExcludedTaskNames(),
                start.isBuildProjectDependencies(),
                start.getCurrentDir(),
                start.getProjectDir(),
                start.isSearchUpwards(),
                projectProperties,
                start.getGradleUserHomeDir(),
                start.getSettingsFile(),
                start.isUseEmptySettings(),
                start.getBuildFile(),
                start.getInitScripts(),
                start.isDryRun(),
                start.isRerunTasks(),
                start.isProfile(),
                start.isContinueOnFailure(),
                start.isOffline(),
                start.getProjectCacheDir(),
                start.isRefreshDependencies(),
                start.isRecompileScripts(),
                start.getParallelThreadCount(),
                start.isConfigureOnDemand()
        );
    }

    private List<TaskExecutionRequest> taskRequests;

    private Set<String> excludedTaskNames;

    private boolean buildProjectDependencies;

    private File currentDir;

    private File projectDir;

    private boolean searchUpwards;

    private List<KeyValue> projectProperties;

    private File gradleUserHomeDir;

    private File settingsFile;

    private boolean useEmptySettings;

    private File buildFile;

    private List<File> initScripts;

    private boolean dryRun;

    private boolean rerunTasks;

    private boolean profile;

    private boolean continueOnFailure;

    private boolean offline;

    private File projectCacheDir;

    private boolean refreshDependencies;

    private boolean recompileScripts;

    private int parallelThreadCount;

    private boolean configureOnDemand;
}
