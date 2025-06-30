package dataset.creation.utils;

import dataset.creation.utils.PipelineUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility per recuperare i tag remoti di un repository Git,
 * ordinarli secondo semver e salvarli in un file.
 */
public class GitTagUtil {

    private static final Logger log = LoggerFactory.getLogger(GitTagUtil.class);

    /**
     * Recupera tutti i tag remoti da 'origin', li ordina e li salva in outputFile.
     *
     * @param git        istanza JGit configurata
     * @param remoteUrl  URL del remote 'origin'
     * @param outputFile percorso del file in cui salvare i tag
     * @return lista ordinata di tag remoti (es. "v4.0.0")
     * @throws Exception in caso di errore I/O o Git
     */
    public static List<String> fetchAndSaveRemoteGitTags(Git git,
                                                         String remoteUrl,
                                                         Path outputFile) throws Exception {
        Collection<Ref> remoteRefs = git.lsRemote()
                .setRemote(remoteUrl)
                .setTags(true)
                .call();

        List<String> tags = remoteRefs.stream()
                .map(Ref::getName)
                .filter(n -> n.startsWith("refs/tags/") && !n.endsWith("^{}"))
                .map(n -> n.substring("refs/tags/".length()))
                .distinct()
                .collect(Collectors.toList());

        // Usa PipelineUtils::compareSemver per ordinare
        tags.sort(PipelineUtils::compareSemver);

        Files.write(outputFile,
                tags,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        log.info("→ Salvati tag Git remoti in {}: {}", outputFile, tags);
        return tags;
    }
    public static List<String> extractOrderedTags(Path repoDir) {
        try (Git git = Git.open(repoDir.toFile())) {
            List<String> tags = git.tagList().call().stream()
                    .map(ref -> ref.getName().replace("refs/tags/", ""))
                    .distinct()
                    .collect(Collectors.toList());

            tags.sort(PipelineUtils::compareSemver);

            log.info("→ Tag trovati localmente in {}: {}", repoDir, tags);
            return tags;
        } catch (Exception e) {
            log.error("❌ Errore durante l'estrazione dei tag locali da {}", repoDir, e);
            return List.of(); // oppure solleva eccezione se preferisci
        }
    }

}
