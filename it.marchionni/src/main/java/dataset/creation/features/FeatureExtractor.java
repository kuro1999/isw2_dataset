package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.SourceCodeProcessor;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.util.datasource.FileDataSource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Estrae le feature essenziali da un file .java, tra cui:
 *  - MethodLength
 *  - ParameterCount
 *  - NestingDepth
 *  - DecisionPoints
 *  - CyclomaticComplexity
 *  - CognitiveComplexity
 *  - CodeSmells (PMD 6.55 violations)
 */
public class FeatureExtractor {

    public Map<String, MethodFeatures> extractFromFile(File javaFile) throws Exception {

        /* ------------------------------------------------------------------
           0) Code Smells via PMD 6.55.0 (SourceCodeProcessor)
           ------------------------------------------------------------------ */
        PMDConfiguration cfg = new PMDConfiguration();
        cfg.setDefaultLanguageVersion(
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getDefaultVersion());

        // carichiamo un ruleset di esempio; cambialo o aggiungine altri se vuoi
        RuleSetFactory rsf = new RuleSetFactory();
        RuleSets ruleSets  = rsf.createRuleSets("category/java/bestpractices.xml");

        RuleContext ctx = new RuleContext();
        ctx.setSourceCodeFilename(javaFile.getAbsolutePath());
        Report report = new Report();
        ctx.setReport(report);

        new SourceCodeProcessor(cfg)             // ► OPZIONE 1: InputStream
                .processSourceCode(new java.io.FileInputStream(javaFile), ruleSets, ctx);        int codeSmellsCount = report.getViolations().size();

        /* ------------------------------------------------------------------
           1) Analisi AST con JavaParser per le altre metriche
           ------------------------------------------------------------------ */
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        Map<String, MethodFeatures> map = new HashMap<>();

        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            MethodFeatures f = new MethodFeatures();

            int begin = md.getBegin().map(p -> p.line).orElse(0);
            int end   = md.getEnd()  .map(p -> p.line).orElse(begin);
            f.methodLength   = end - begin + 1;
            f.parameterCount = md.getParameters().size();

            DepthVisitor dv = new DepthVisitor();
            dv.visit(md, 0);
            f.nestingDepth        = dv.maxDepth;
            f.decisionPoints      = dv.decisionPoints;
            f.cyclomaticComplexity= f.decisionPoints + 1;
            f.cognitiveComplexity = f.decisionPoints;

            f.codeSmells = codeSmellsCount;

            String sig = md.getDeclarationAsString(false, false, false);
            map.put(sig, f);
        }
        return map;
    }

    /* ---------------- helper per Nesting Depth & Decision Points ---------- */
    private static class DepthVisitor extends VoidVisitorAdapter<Integer> {
        int maxDepth = 0, decisionPoints = 0;

        @Override public void visit(com.github.javaparser.ast.stmt.IfStmt n, Integer d) {
            super.visit(n, d);
            decisionPoints++; int nd = d + 1; maxDepth = Math.max(maxDepth, nd);
            n.getThenStmt().accept(this, nd);
            n.getElseStmt().ifPresent(e -> e.accept(this, nd));
        }
        @Override public void visit(com.github.javaparser.ast.stmt.ForStmt n, Integer d) {
            super.visit(n, d);
            decisionPoints++; int nd = d + 1; maxDepth = Math.max(maxDepth, nd);
            n.getBody().accept(this, nd);
        }
        @Override public void visit(com.github.javaparser.ast.stmt.WhileStmt n, Integer d) {
            super.visit(n, d);
            decisionPoints++; int nd = d + 1; maxDepth = Math.max(maxDepth, nd);
            n.getBody().accept(this, nd);
        }
        @Override public void visit(com.github.javaparser.ast.stmt.DoStmt n, Integer d) {
            super.visit(n, d);
            decisionPoints++; int nd = d + 1; maxDepth = Math.max(maxDepth, nd);
            n.getBody().accept(this, nd);
        }
        @Override public void visit(com.github.javaparser.ast.stmt.SwitchEntry n, Integer d) {
            super.visit(n, d);
            decisionPoints++; int nd = d + 1; maxDepth = Math.max(maxDepth, nd);
            n.getStatements().forEach(s -> s.accept(this, nd));
        }
    }

    /* ---------------- DTO ---------------- */
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
