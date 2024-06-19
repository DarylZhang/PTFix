package edu.swinburne.xwzhang.ptfix.ptfix;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Optional;

public class FilePathValidationModifier extends ModifierVisitor<String> {

    @Override
    public Visitable visit(AssignExpr assignExpr, String vulnerableExpr) {

        // 检查是否是我们要找的那行代码
        if (assignExpr.toString().equals(vulnerableExpr)) {
            String declaredFileName = null;
            String fileFirstArgName = null;
            String fileFirstArgType = null;

            NameExpr nameExpr = (NameExpr) assignExpr.getTarget();
            declaredFileName = nameExpr.getNameAsString();

            // 检查赋值右侧是否为对象创建表达式
            if (assignExpr.getValue() instanceof ObjectCreationExpr) {
                ObjectCreationExpr creationExpr = (ObjectCreationExpr) assignExpr.getValue();

                if (!creationExpr.getArguments().isEmpty()) {
                    Expression firstArgExpr = creationExpr.getArguments().get(0);

                    fileFirstArgName = firstArgExpr.toString();

                    try {
                        firstArgExpr.asNameExpr().resolve().getType();

                        ResolvedType declaringType = ((NameExpr) firstArgExpr).calculateResolvedType();
                        fileFirstArgType = declaringType.describe();
                    } catch (Exception e) {
                        System.out.println("Unable to resolve type for: " + firstArgExpr.toString());
                    }
                }
            }

            // 创建 if 语句的条件
            Expression condition = null;
            if ("java.io.File".equals(fileFirstArgType)) {
                condition = new MethodCallExpr(
                        new MethodCallExpr(new NameExpr(declaredFileName), "getCanonicalPath"),
                        "startsWith",
                        NodeList.nodeList(new MethodCallExpr(new NameExpr(fileFirstArgName), "getCanonicalPath"))
                );
            } else {
                condition = new MethodCallExpr(
                        new MethodCallExpr(new NameExpr(declaredFileName), "getCanonicalPath"),
                        "startsWith",
                        NodeList.nodeList(new NameExpr(fileFirstArgName))
                );
            }

            UnaryExpr notCondition = new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);

            // 创建 if 语句的 then 部分
            BlockStmt thenStmt = new BlockStmt();
            thenStmt.addStatement(new ThrowStmt(new NameExpr("IllegalArgumentException()")));

            // 创建整个 if 语句
            IfStmt ifStmt = new IfStmt(notCondition, thenStmt, null);

            // 将 if 语句添加到变量声明后面
            BlockStmt block = (BlockStmt) assignExpr.getParentNode().get().getParentNode().get();

            NodeList<Statement> stmts = block.getStatements();
            for (int i = 0; i < stmts.size(); i++) {
                if (stmts.get(i).toString().contains(vulnerableExpr)) {
                    stmts.add(i+1, ifStmt);
                    break;
                }
            }
        }

        return assignExpr;
    }

    @Override
    public Visitable visit(VariableDeclarationExpr variableDeclarationExpr, String vulnerableExpr) {

        // 检查是否是我们要找的那行代码
        if (variableDeclarationExpr.toString().equals(vulnerableExpr)) {

            if(variableDeclarationExpr.toString().contains("URI.create")) {
                BlockStmt block = (BlockStmt) variableDeclarationExpr.getParentNode().get().getParentNode().get();

                MethodCallExpr normalizeCallExpr = new MethodCallExpr(variableDeclarationExpr, "normalize");

                NodeList<Statement> stmts = block.getStatements();
                for (int i = 0; i < stmts.size(); i++) {
                    if (stmts.get(i).toString().contains(vulnerableExpr)) {
                        stmts.remove(stmts.get(i));
                        stmts.add(i, new ExpressionStmt(normalizeCallExpr));
                        break;
                    }
                }
            }

            if (variableDeclarationExpr.toString().contains("new File")) {
                String declaredFileName = null;
                String fileFirstArgName = null;
                String fileFirstArgType = null;

                // 提取变量声明的名字（例如 privateFile）
                for (VariableDeclarator var : variableDeclarationExpr.getVariables()) {
                    declaredFileName = var.getName().asString();

                    // 查找 ObjectCreationExpr (例如 new File())
                    if (var.getInitializer().isPresent() && var.getInitializer().get() instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creationExpr = (ObjectCreationExpr) var.getInitializer().get();

                        if (!creationExpr.getArguments().isEmpty()) {
                            Expression firstArgExpr = creationExpr.getArguments().get(0);

                            fileFirstArgName = firstArgExpr.toString();

                            try {
                                firstArgExpr.asNameExpr().resolve().getType();

                                ResolvedType declaringType = ((NameExpr) firstArgExpr).calculateResolvedType();
                                fileFirstArgType = declaringType.describe();
                            } catch (Exception e) {
                                System.out.println("Unable to resolve type for: " + firstArgExpr.toString());
                            }
                        }
                    }
                }

                // 创建 if 语句的条件
                Expression condition = null;
                if ("java.io.File".equals(fileFirstArgType)) {
                    condition = new MethodCallExpr(
                            new MethodCallExpr(new NameExpr(declaredFileName), "getCanonicalPath"),
                            "startsWith",
                            NodeList.nodeList(new MethodCallExpr(new NameExpr(fileFirstArgName), "getCanonicalPath"))
                    );
                } else {
                    condition = new MethodCallExpr(
                            new MethodCallExpr(new NameExpr(declaredFileName), "getCanonicalPath"),
                            "startsWith",
                            NodeList.nodeList(new NameExpr(fileFirstArgName))
                    );
                }

                UnaryExpr notCondition = new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);

                // 创建 if 语句的 then 部分
                BlockStmt thenStmt = new BlockStmt();
                thenStmt.addStatement(new ThrowStmt(new NameExpr("IllegalArgumentException()")));

                // 创建整个 if 语句
                IfStmt ifStmt = new IfStmt(notCondition, thenStmt, null);

                // 将 if 语句添加到变量声明后面
                BlockStmt block = (BlockStmt) variableDeclarationExpr.getParentNode().get().getParentNode().get();

                NodeList<Statement> stmts = block.getStatements();
                for (int i = 0; i < stmts.size(); i++) {
                    if (stmts.get(i).toString().contains(vulnerableExpr)) {
                        stmts.add(i+1, ifStmt);
                        break;
                    }
                }
            }

        }

        return variableDeclarationExpr;
    }
}
