package dataset.creation.fetcher.model;

import java.time.Instant;

public class Release {
    public String tagName;
    public Instant publishedAt;

    public Release(String tagName, Instant publishedAt) {
        this.tagName = tagName;
        this.publishedAt = publishedAt;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
    public String getTagName() {
        return tagName;
    }
    public Instant getPublishedAt() {
        return publishedAt;
    }
}
