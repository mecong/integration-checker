package com.mecong.maven.plugin.validator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.google.gson.Gson;
import com.mecong.maven.plugin.annotations.IntegrationDtoBound;
import com.mecong.maven.plugin.annotations.IntegrationDtoExpose;
import com.mecong.maven.plugin.validator.dto.BoundDtoInfo;
import com.mecong.maven.plugin.validator.dto.DtoInfo;
import com.mecong.maven.plugin.validator.dto.SimpleFieldDeclaration;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

@Mojo(name = "integration-checker", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class IntegrationCheckerMojo extends AbstractMojo {

    public static final String APPLICATION_JSON = "application/json";
    private final JavaParser parser = new JavaParser();
    private final Gson gson = new Gson();
    List<DtoInfo> dtoExposeList = new ArrayList<>();
    List<BoundDtoInfo> dtoVerifyList = new ArrayList<>();

    @Getter
    @Setter
    private Log log;
    /**
     * Gives access to the Maven project information.
     */

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    /**
     * List of regexp of git branches for which the plugin should be executed
     */

    @Parameter(property = "branches", required = true, readonly = true)
    private List<String> allowedGitBranches;

    /**
     * Integration information storage endpoint
     */
    @Parameter(property = "integration-checker.url", required = true, readonly = true)
    private String integrationCheckerUrl;

    @Parameter(defaultValue = "${project.compileSourceRoots}", required = true, readonly = true)
    private List<String> compileSourceRoots;

    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}", readonly = true)
    private String parentDir;

    @Inject
    private DtoValidationService dtoValidationService;

    @Inject
    private GitService gitService;

    public void execute() throws MojoExecutionException {

        log.info("Starting");
        if (gitService.isCurrentGitBranchForbidden(allowedGitBranches, parentDir, log)) {
            log.info("Current git branch is not allowed. Exiting.");
            return;
        }

        log.debug("SOURCES: " + compileSourceRoots);

        compileSourceRoots.forEach(root -> traverseRecursively(new File(root)));

        dtoExposeList.forEach(this::postDto);
        for (BoundDtoInfo boundDtoInfo : dtoVerifyList) {
            dtoValidationService.verifyDto(boundDtoInfo, integrationCheckerUrl, log);
        }
    }

    private void traverseRecursively(File file) {
        if (file.isDirectory()) {
            log.debug("TRAVERSE DIR: " + file);
            Arrays.stream(Objects.requireNonNull(file.listFiles((dir, name) -> !name.equals("..") && !name.equals("."))))
                    .forEach(this::traverseRecursively);
        } else if (file.getName().endsWith(".java")) {
            processSourceFile(file);
        }
    }

    @SneakyThrows
    private void processSourceFile(File file) {

        ParseResult<CompilationUnit> cu = parser.parse(new FileInputStream(file));
        Optional<CompilationUnit> result = cu.getResult();

        if (result.isPresent()) {
            CompilationUnit compilationUnit = result.get();
            Optional<PackageDeclaration> packageDeclaration = compilationUnit.getPackageDeclaration();

            compilationUnit.getTypes().forEach(type -> {
                String typeName = type.getName().getIdentifier();
                String fullTypeName = packageDeclaration
                        .map(declaration -> declaration.getNameAsString() + "." + typeName)
                        .orElse(typeName);

                Optional<AnnotationExpr> integrationExposeAnnotation = type.getAnnotations().stream()
                        .filter(d -> d.getName().getIdentifier().equals(IntegrationDtoExpose.class.getSimpleName()))
                        .findFirst();
                if (integrationExposeAnnotation.isPresent()) {
                    processIntegrationExpose(compilationUnit, project.getArtifactId() + "_" + fullTypeName);
                }

                Optional<AnnotationExpr> integrationBoundAnnotation = type.getAnnotations().stream()
                        .filter(d -> d.getName().getIdentifier().equals(IntegrationDtoBound.class.getSimpleName()))
                        .findFirst();
                integrationBoundAnnotation
                        .ifPresent(annotationExpr -> processIntegrationBinding(compilationUnit, annotationExpr, fullTypeName));
            });
        }
    }

    private void processIntegrationBinding(CompilationUnit compilationUnit, AnnotationExpr annotationExpr, String fullTypeName) {
        Node node = annotationExpr.getChildNodes().get(1);
        String boundId = node instanceof MemberValuePair ?
                ((StringLiteralExpr) ((MemberValuePair) node).getValue()).getValue() :
                node.toString();

        boundId = boundId.replace("\"", "");

        List<SimpleFieldDeclaration> simpleFieldDeclarations = collectFields(compilationUnit);
        if (log.isDebugEnabled()) {
            log.debug("Found fields in type: " + simpleFieldDeclarations);
        }

        dtoVerifyList.add(new BoundDtoInfo(fullTypeName, boundId, simpleFieldDeclarations.toString()));
    }

    private void processIntegrationExpose(CompilationUnit compilationUnit, String dtoId) {

        List<SimpleFieldDeclaration> simpleFieldDeclarations = collectFields(compilationUnit);
        if (log.isDebugEnabled()) {
            log.debug("Found fields in type: " + simpleFieldDeclarations);
        }

        dtoExposeList.add(new DtoInfo(dtoId, simpleFieldDeclarations.toString()));
    }

    private List<SimpleFieldDeclaration> collectFields(CompilationUnit compilationUnit) {
        return compilationUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.asFieldDeclaration().getVariables().stream())
                .map(f -> new SimpleFieldDeclaration(f.getNameAsString(), f.getTypeAsString()))
                .sorted()
                .collect(Collectors.toList());
    }

    private void postDto(DtoInfo dtoInfo) {
        log.info("Posting resource: " + integrationCheckerUrl + "/" + dtoInfo.getId());

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(integrationCheckerUrl);

            String dataToWrite = gson.toJson(dtoInfo);
            StringEntity entity = new StringEntity(dataToWrite);
            httpPost.setEntity(entity);
            httpPost.setHeader(ACCEPT, APPLICATION_JSON);
            httpPost.setHeader(CONTENT_TYPE, APPLICATION_JSON);

            CloseableHttpResponse response = client.execute(httpPost);

            if (log.isDebugEnabled()) {
                log.debug("Post: " + dataToWrite);
                Scanner sc = new Scanner(response.getEntity().getContent());

                log.debug("Response: " + response.getStatusLine().toString());
                while (sc.hasNext()) {
                    log.debug(sc.nextLine());
                }
                log.debug("-------------");
            }
        } catch (IOException e) {
            log.error("Cannot post to " + integrationCheckerUrl + " Error: " + e.getMessage());
        }
    }
}
