package dataset.creation.features.metrics;

public class MethodMetrics {
    private StructuralChangeMetrics structural;
    private ComplexityMetrics complexity;
    private ElseMetrics elseMetrics;
    private AddDeleteMetrics addDelete;

    public MethodMetrics(StructuralChangeMetrics structural,
                         ComplexityMetrics complexity,
                         ElseMetrics elseMetrics,
                         AddDeleteMetrics addDelete) {
        this.structural  = structural;
        this.complexity  = complexity;
        this.elseMetrics = elseMetrics;
        this.addDelete   = addDelete;
    }

    public StructuralChangeMetrics getStructural() { return structural; }
    public ComplexityMetrics     getComplexity() { return complexity; }
    public ElseMetrics           getElseMetrics() { return elseMetrics; }
    public AddDeleteMetrics      getAddDelete() { return addDelete; }
    public void SetStructural(StructuralChangeMetrics structural) { this.structural = structural; }
    public void SetComplexity(ComplexityMetrics complexity) { this.complexity = complexity; }
    public void SetElse(ElseMetrics elseMetrics) { this.elseMetrics = elseMetrics; }
    public void SetAdd(AddDeleteMetrics addDelete) { this.addDelete = addDelete; }
}
