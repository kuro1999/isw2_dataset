package dataset.creation.features;

public class MethodFeatures {
    private int methodLength;
    private int parameterCount;
    private int nestingDepth;
    private int decisionPoints;
    private int cyclomaticComplexity;
    private int cognitiveComplexity;
    private int codeSmells;

    public MethodFeatures() {
        //empty
    }

    public int getMethodLength() {
        return methodLength;
    }
    public void setMethodLength(int methodLength) {
        this.methodLength = methodLength;
    }
    public int getParameterCount() {
        return parameterCount;
    }
    public void setParameterCount(int parameterCount) {
        this.parameterCount = parameterCount;
    }
    public int getNestingDepth() {
        return nestingDepth;
    }
    public void setNestingDepth(int nestingDepth) {
        this.nestingDepth = nestingDepth;
    }
    public int getDecisionPoints() {
        return decisionPoints;
    }
    public void setDecisionPoints(int decisionPoints) {
        this.decisionPoints = decisionPoints;
    }
    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }
    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }
    public int getCognitiveComplexity() {
        return cognitiveComplexity;
    }
    public void setCognitiveComplexity(int cognitiveComplexity) {
        this.cognitiveComplexity = cognitiveComplexity;
    }
    public int getCodeSmells() {
        return codeSmells;
    }
    public void setCodeSmells(int codeSmells) {
        this.codeSmells = codeSmells;
    }

}

