package dataset.creation.features.metrics;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public class MethodMetrics {
    private StructuralChangeMetrics structural;
    private ComplexityMetrics complexity;
    private ElseMetrics elseMetrics;
    private AddDeleteMetrics addDelete;

    @JsonbCreator
    public MethodMetrics(
            @JsonbProperty("structural") StructuralChangeMetrics structural,
            @JsonbProperty("complexity")  ComplexityMetrics complexity,
            @JsonbProperty("elseMetrics") ElseMetrics elseMetrics,
            @JsonbProperty("addDelete")   AddDeleteMetrics addDelete
    ) {
        this.structural  = structural;
        this.complexity  = complexity;
        this.elseMetrics = elseMetrics;
        this.addDelete   = addDelete;
    }
    public MethodMetrics() {
        //empty
    }

    public StructuralChangeMetrics getStructural() { return structural; }
    public ComplexityMetrics getComplexity() { return complexity; }
    public ElseMetrics getElseMetrics() { return elseMetrics; }
    public AddDeleteMetrics getAddDelete() { return addDelete; }
}
