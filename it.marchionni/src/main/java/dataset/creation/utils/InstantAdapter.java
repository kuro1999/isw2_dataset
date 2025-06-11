package dataset.creation.utils;

import jakarta.json.bind.adapter.JsonbAdapter;

import java.time.Instant;

public class InstantAdapter implements JsonbAdapter<Instant, String> {
    @Override public String adaptToJson(Instant obj) { return obj.toString(); }
    @Override public Instant adaptFromJson(String s) { return Instant.parse(s); }
}
