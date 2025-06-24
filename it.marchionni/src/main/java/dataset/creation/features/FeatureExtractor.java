package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Estrae le feature essenziali da un file .java:
 *  - MethodLength
 *  - ParameterCount
 *  - NestingDepth
 *  - DecisionPoints
 *  - CyclomaticComplexity = decisionPoints + 1
 *  - CognitiveComplexity  = decisionPoints (proxy semplice)
 *  - CodeSmells = 0 (placeholder)
 */
public class FeatureExtractor {

    public Map<String, MethodFeatures> extractFromFile(File javaFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        Map<String, MethodFeatures> result = new HashMap<>();

        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            MethodFeatures f = new MethodFeatures();

            // 1) MethodLength (linee)
            int begin = md.getBegin().map(p -> p.line).orElse(0);
            int end   = md.getEnd().  map(p -> p.line).orElse(begin);
            f.methodLength = end - begin + 1;

            // 2) ParameterCount
            f.parameterCount = md.getParameters().size();

            // 3) NestingDepth & DecisionPoints
            DepthVisitor dv = new DepthVisitor();
            dv.visit(md, 0);
            f.nestingDepth   = dv.max;
            f.decisionPoints = dv.decisionPoints;

            // 4) CyclomaticComplexity = decisionPoints + 1
            f.cyclomaticComplexity = f.decisionPoints + 1;

            // 5) CognitiveComplexity = decisionPoints (proxy)
            f.cognitiveComplexity = f.decisionPoints;

            // 6) CodeSmells = 0 (da integrare in seguito)
            f.codeSmells = 0;

            // identificatore univoco del metodo
            String sig = md.getDeclarationAsString(false, false, false);
            result.put(sig, f);
        }
        return result;
    }

    /** Visitor che conta nesting depth e decision points */
    private static class DepthVisitor extends VoidVisitorAdapter<Integer> {
        int max = 0;
        int decisionPoints = 0;

        @Override
        public void visit(IfStmt n, Integer depth) {
            super.visit(n, depth);
            decisionPoints++;
            int nd = depth + 1;
            max = Math.max(max, nd);
            n.getThenStmt().accept(this, nd);
            n.getElseStmt().ifPresent(e -> e.accept(this, nd));
        }

        @Override
        public void visit(ForStmt n, Integer depth) {
            super.visit(n, depth);
            decisionPoints++;
            int nd = depth + 1;
            max = Math.max(max, nd);
            n.getBody().accept(this, nd);
        }

        @Override
        public void visit(WhileStmt n, Integer depth) {
            super.visit(n, depth);
            decisionPoints++;
            int nd = depth + 1;
            max = Math.max(max, nd);
            n.getBody().accept(this, nd);
        }

        @Override
        public void visit(DoStmt n, Integer depth) {
            super.visit(n, depth);
            decisionPoints++;
            int nd = depth + 1;
            max = Math.max(max, nd);
            n.getBody().accept(this, nd);
        }

        @Override
        public void visit(SwitchEntry n, Integer depth) {
            super.visit(n, depth);
            decisionPoints++;
            int nd = depth + 1;
            max = Math.max(max, nd);
            n.getStatements().forEach(s -> s.accept(this, nd));
        }
    }

    /** DTO per le feature di un metodo */
    public static class MethodFeatures {
        public int methodLength;
        public int parameterCount;
        public int nestingDepth;
        public int decisionPoints;
        public int cyclomaticComplexity;
        public int cognitiveComplexity;
        public int codeSmells;
    }
}
