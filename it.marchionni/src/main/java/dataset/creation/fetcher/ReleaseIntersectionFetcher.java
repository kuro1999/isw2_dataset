package dataset.creation.fetcher;

import dataset.creation.fetcher.model.Release;
import dataset.creation.fetcher.jira.JiraVersion;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ReleaseIntersectionFetcher {

    private final GitInjection   gitInj;
    private final JiraInjection  jiraInj;

    public ReleaseIntersectionFetcher(String gitRemoteUrl,
                                      File gitWorkDir,
                                      List<Release> gitReleasesTemplate,
                                      String jiraProjectKey) throws Exception {
        // 1) Inizializza e scarica Git
        this.gitInj = new GitInjection(gitRemoteUrl, gitWorkDir, gitReleasesTemplate);
        this.gitInj.injectCommits();

        // 2) Inizializza e scarica JIRA
        this.jiraInj = new JiraInjection(jiraProjectKey);
        this.jiraInj.injectReleases();
    }

    /**
     * Restituisce la lista delle Release “comuni” a Git e JIRA,
     * confrontando i tag Git (Release.getTag()) con i nomi JIRA (JiraVersion.getName()).
     */
    public List<Release> getCommonByName() {
        // set di nomi JIRA
        Set<String> jiraNames = jiraInj.getReleases()
                .stream()
                .map(JiraVersion::getName)
                .collect(Collectors.toSet());
        // filtro le release Git il cui tag è presente in JIRA
        return gitInj.getReleases()
                .stream()
                .filter(r -> jiraNames.contains(r.getTag()))
                .collect(Collectors.toList());
    }

    /**
     * Se preferisci fare l’intersezione per data di rilascio esatta:
     */
    public List<Release> getCommonByDate() {
        Set<LocalDate> jiraDates = jiraInj.getReleases()
                .stream()
                .map(JiraVersion::getReleaseDate)
                .collect(Collectors.toSet());
        return gitInj.getReleases()
                .stream()
                .filter(r -> jiraDates.contains(r.getReleaseDate()))
                .collect(Collectors.toList());
    }
}
