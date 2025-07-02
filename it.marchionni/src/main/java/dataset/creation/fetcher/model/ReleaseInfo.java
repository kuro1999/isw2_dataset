package dataset.creation.fetcher.model;

import java.time.LocalDate;

public interface ReleaseInfo {
    String getTag();
    LocalDate getReleaseDate();
}
