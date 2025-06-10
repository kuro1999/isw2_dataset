package dataset.creation.fetcher;

import dataset.creation.fetcher.model.Release;
import dataset.creation.fetcher.model.JiraIssue;
import okhttp3.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class BookkeeperFetcher {
    private static final String GITHUB_API = "https://api.github.com/repos/apache/bookkeeper/releases";
    private static final String JIRA_API   = "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_JQL   = "project=BOOKKEEPER+AND+fixVersion=\"%s\"";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Release> fetchAllReleases(String githubToken) throws IOException {
        Request req = new Request.Builder()
                .url(GITHUB_API)
                .header("Authorization", "token " + githubToken)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error: " + resp.code());
            }
            return mapper.readValue(resp.body().byteStream(),
                    new TypeReference<List<Release>>() {});
        }
    }

    public List<Release> filterNewest34Percent(List<Release> all) {
        List<Release> sorted = all.stream()
                .sorted(Comparator.comparing(r -> r.getPublishedAt()))
                .collect(Collectors.toList());
        int cutoff = (int) Math.ceil(sorted.size() * 0.66);
        return sorted.subList(cutoff, sorted.size());
    }

    public List<JiraIssue> fetchIssuesForVersion(String version,
                                                 String jiraUser,
                                                 String jiraPass) throws IOException {
        String jql = String.format(JIRA_JQL, version);
        HttpUrl url = HttpUrl.parse(JIRA_API).newBuilder()
                .addQueryParameter("jql", jql)
                .addQueryParameter("maxResults", "1000")
                .build();

        String credential = Credentials.basic(jiraUser, jiraPass);
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", credential)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("JIRA API error: " + resp.code());
            }
            JsonNode root = mapper.readTree(resp.body().byteStream());
            List<JiraIssue> list = new ArrayList<>();
            for (JsonNode node : root.path("issues")) {
                String key     = node.get("key").asText();
                String summary = node.path("fields").path("summary").asText();
                String status  = node.path("fields").path("status").path("name").asText();
                // usa il costruttore esistente: JiraIssue(String key, String summary, String status, String fixVersion)
                JiraIssue ji = new JiraIssue(key, summary, status, version);
                list.add(ji);
            }

            return list;
        }
    }
}

