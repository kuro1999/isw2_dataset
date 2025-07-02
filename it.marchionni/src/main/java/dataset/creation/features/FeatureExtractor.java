package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dataset.creation.exceptions.FeatureExtractionException;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetLoader;
import net.sourceforge.pmd.renderers.AbstractRenderer;
import net.sourceforge.pmd.renderers.Renderer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Estrae le feature essenziali da un file .java, tra cui:
 *   • MethodLength, ParameterCount, NestingDepth, DecisionPoints
 *   • Cyclomatic / Cognitive Complexity
 *   • CodeSmells (PMD 6.55 violations, API non deprecate)
 */
public class FeatureExtractor {

    public Map<String, MethodFeatures> extractFromFile(File javaFile)
            throws FeatureExtractionException {

        try {
            Logger.getLogger("net.sourceforge.pmd").setLevel(Level.SEVERE);

            /* ---- 0) PMD code-smells con renderer custom -------------------- */
            PMDConfiguration cfg = new PMDConfiguration();
            cfg.setDefaultLanguageVersion(
                    LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getDefaultVersion());

            RuleSet ruleSet = new RuleSetLoader()
                    .loadFromResource("category/java/bestpractices.xml");

            final int[] smellCounter = {0};

            Renderer collector = new AbstractRenderer("collector", "counts violations") {

                @Override public String defaultFileExtension() { return "txt"; }
                @Override public void start() {
                    //empty
                }
                @Override public void startFileAnalysis(
                        net.sourceforge.pmd.util.datasource.DataSource d) {
                    //empty
                }
                @Override public void renderFileReport(Report rpt) {
                    smellCounter[0] += rpt.getViolations().size();
                }
                @Override public void end() {
                    //empty
                }
            };

            try (PmdAnalysis pmd = PmdAnalysis.create(cfg)) {
                pmd.addRuleSet(ruleSet);
                pmd.files().addFile(javaFile.toPath());
                pmd.addRenderer(collector);
                pmd.performAnalysis();
            }
            int codeSmellsCount = smellCounter[0];

            /* ---- 1) JavaParser AST metrics -------------------------------- */
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            Map<String, MethodFeatures> map = new HashMap<>();

            for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                MethodFeatures f = new MethodFeatures();

                int begin = md.getBegin().map(p -> p.line).orElse(0);
                int end   = md.getEnd()  .map(p -> p.line).orElse(begin);
                f.setMethodLength(end - begin + 1);
                f.setParameterCount(md.getParameters().size());

                DepthVisitor dv = new DepthVisitor();
                dv.visit(md, 0);
                f.setNestingDepth(dv.maxDepth);
                f.setDecisionPoints(dv.decisionPoints);
                f.setCyclomaticComplexity(f.getDecisionPoints() + 1);
                f.setCognitiveComplexity(f.getDecisionPoints());

                f.setCodeSmells(codeSmellsCount);

                String sig = md.getDeclarationAsString(false, false, false);
                map.put(sig, f);
            }
            return map;

        } catch (Exception e) {
            throw new FeatureExtractionException(
                    "Errore estraendo metriche da " + javaFile.getName(), e);
        }
    }


    /* ------------ helper per NestingDepth & DecisionPoints --------------- */
    private static class DepthVisitor extends VoidVisitorAdapter<Integer> {
        int maxDepth = 0;
        int decisionPoints = 0;

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
}
