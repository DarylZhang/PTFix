package edu.swinburne.xwzhang.ptfix.ptfix;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.Log;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PtFixApplication {

    public static void main(String[] args) {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));

        JavaParser parser = new JavaParser(parserConfiguration);

        Path pathToSourceRoot = Paths.get("src/main/resources/data/Path Traversal/test");
        SourceRoot sourceRoot = new SourceRoot(pathToSourceRoot, parserConfiguration);

        try {
            Files.walkFileTree(pathToSourceRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        try {
                            CompilationUnit cu = sourceRoot.parse("", pathToSourceRoot.relativize(file).toString());

                            System.out.println("File name: " + file.getFileName());

                            String vulnerableExpr = findVulnerableExpr(cu);
                            cu.accept(new FilePathValidationModifier(), vulnerableExpr);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


//        SourceRoot sourceRoot = new SourceRoot(Util.gradleModuleRoot(PtFixApplication.class).resolve("src/main/resources/data/Path Traversal"), parserConfiguration);

        // Our sample is in the root of this directory, so no package name.
//        CompilationUnit cu = sourceRoot.parse("", "CVE-2017-20181_bf.java");
//
//        String vulnerableExpr = findVulnerableExpr(cu);
//
//        cu.accept(new FilePathValidationModifier(), vulnerableExpr);

        // This saves all the files we just read to an output directory.
        sourceRoot.saveAll(
                // The path of the Maven module/project which contains the LogicPositivizer class.
                CodeGenerationUtils.mavenModuleRoot(PtFixApplication.class)
                        // appended with a path to "output"
                        .resolve(Paths.get("output")));
    }

    public static String findVulnerableExpr(CompilationUnit cu) {

        String vulnerableExpr = null;

        for (Comment comment : cu.getAllContainedComments()) {
            if (comment.toString().contains("//Path Traversal Vul")) {
                Optional<Node> followingNode = comment.getCommentedNode();

                if (followingNode.isPresent()) {
                    vulnerableExpr = followingNode.get().getTokenRange().get().toString();
                    vulnerableExpr = vulnerableExpr.substring(0, vulnerableExpr.length() - 1);
                }
            }
        }

        return vulnerableExpr;
    }
}
