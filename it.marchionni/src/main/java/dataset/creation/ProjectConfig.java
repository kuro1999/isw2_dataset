package dataset.creation;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class ProjectConfig {

    private final String owner;
    private final String repo;
    private final String jiraProject;
    private final String releaseCut;   // può essere null
    private final Map<String,String> extraEnv;

    public ProjectConfig(String owner,
                         String repo,
                         String jiraProject,
                         String releaseCut,
                         Map<String,String> extraEnv) {

        this.owner       = Objects.requireNonNull(owner);
        this.repo        = Objects.requireNonNull(repo);
        this.jiraProject = Objects.requireNonNull(jiraProject);
        this.releaseCut  = releaseCut;               // nullable
        this.extraEnv   = extraEnv == null ? Map.of() : extraEnv;
    }

    public String owner()       { return owner; }
    public String repo()        { return repo; }
    public String jiraProject() { return jiraProject; }
    public String releaseCut()  { return releaseCut; }
    public Map<String,String> extraEnv() { return extraEnv; }
}
