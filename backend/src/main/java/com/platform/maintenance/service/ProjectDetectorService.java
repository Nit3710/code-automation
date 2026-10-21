package com.platform.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.enums.ProjectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ProjectDetectorService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RepositoryDtos.ProjectDetectionResult detect(File projectDir) {
        List<String> matchedIndicators = new ArrayList<>();

        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory()) {
            return buildResult(ProjectType.UNKNOWN, matchedIndicators, 0.0, null, null, null);
        }

        File pomXml = new File(projectDir, "pom.xml");
        File buildGradle = new File(projectDir, "build.gradle");
        File buildGradleKts = new File(projectDir, "build.gradle.kts");
        File packageJson = new File(projectDir, "package.json");
        File requirementsTxt = new File(projectDir, "requirements.txt");
        File pyprojectToml = new File(projectDir, "pyproject.toml");
        File setupPy = new File(projectDir, "setup.py");

        // 1. Check for Python projects
        if (requirementsTxt.exists() || pyprojectToml.exists() || setupPy.exists() || hasPythonFiles(projectDir)) {
            if (requirementsTxt.exists()) matchedIndicators.add("requirements.txt");
            if (pyprojectToml.exists()) matchedIndicators.add("pyproject.toml");
            if (setupPy.exists()) matchedIndicators.add("setup.py");

            String installCmd = requirementsTxt.exists() ? "pip install -r requirements.txt" : "";
            String testCmd = "python -m unittest discover";

            // Find specific test files in root directory
            File testAgent = new File(projectDir, "test_agent.py");
            File testCommands = new File(projectDir, "test_commands.py");
            File mockTest = new File(projectDir, "mock_test.py");

            if (testAgent.exists()) {
                testCmd = "python test_agent.py";
                matchedIndicators.add("test_agent.py");
            } else if (mockTest.exists()) {
                testCmd = "python mock_test.py";
                matchedIndicators.add("mock_test.py");
            } else if (testCommands.exists()) {
                testCmd = "python test_commands.py";
                matchedIndicators.add("test_commands.py");
            }

            return buildResult(ProjectType.PYTHON, matchedIndicators, 0.95, installCmd, "", testCmd);
        }

        // 2. Check for Java Maven (Spring Boot / JUnit)
        if (pomXml.exists()) {
            matchedIndicators.add("pom.xml");
            try {
                String pomContent = Files.readString(pomXml.toPath());
                if (pomContent.contains("spring-boot")) {
                    matchedIndicators.add("Spring Boot dependency");
                }
                if (pomContent.contains("junit")) {
                    matchedIndicators.add("JUnit test suite");
                }
            } catch (IOException e) {
                log.warn("Could not inspect pom.xml content", e);
            }
            return buildResult(ProjectType.JAVA_MAVEN, matchedIndicators, 0.95,
                    "mvn dependency:resolve", "mvn clean compile", "mvn test");
        }

        // 3. Check for Java Gradle
        if (buildGradle.exists() || buildGradleKts.exists()) {
            matchedIndicators.add(buildGradle.exists() ? "build.gradle" : "build.gradle.kts");
            return buildResult(ProjectType.JAVA_GRADLE, matchedIndicators, 0.90,
                    "./gradlew dependencies", "./gradlew classes", "./gradlew test");
        }

        // 4. Check for Next.js / React / Node.js
        if (packageJson.exists()) {
            matchedIndicators.add("package.json");
            File nextConfigJs = new File(projectDir, "next.config.js");
            File nextConfigMjs = new File(projectDir, "next.config.mjs");
            File nextConfigTs = new File(projectDir, "next.config.ts");

            try {
                JsonNode root = objectMapper.readTree(packageJson);
                JsonNode dependencies = root.get("dependencies");
                JsonNode devDependencies = root.get("devDependencies");

                boolean hasNext = (dependencies != null && dependencies.has("next")) ||
                                  (devDependencies != null && devDependencies.has("next")) ||
                                  nextConfigJs.exists() || nextConfigMjs.exists() || nextConfigTs.exists();

                boolean hasReact = (dependencies != null && dependencies.has("react")) ||
                                   (devDependencies != null && devDependencies.has("react"));

                if (hasNext) {
                    matchedIndicators.add("next.js dependency / config");
                    return buildResult(ProjectType.NEXT_JS, matchedIndicators, 0.95,
                            "npm install", "npm run build", "npm test");
                } else if (hasReact) {
                    matchedIndicators.add("react dependency");
                    return buildResult(ProjectType.REACT_NPM, matchedIndicators, 0.90,
                            "npm install", "npm run build", "npm test -- --watchAll=false");
                } else {
                    return buildResult(ProjectType.GENERIC_NODE, matchedIndicators, 0.85,
                            "npm install", "npm run build", "npm test");
                }
            } catch (Exception e) {
                log.warn("Could not parse package.json", e);
                return buildResult(ProjectType.GENERIC_NODE, matchedIndicators, 0.70,
                        "npm install", "npm run build", "npm test");
            }
        }

        return buildResult(ProjectType.UNKNOWN, matchedIndicators, 0.0, "", "", "");
    }

    private boolean hasPythonFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".py"));
        return files != null && files.length > 0;
    }

    private RepositoryDtos.ProjectDetectionResult buildResult(
            ProjectType type, List<String> indicators, double confidence,
            String customInstall, String customBuild, String customTest) {

        String install = customInstall != null ? customInstall : type.getDefaultBuildCommand();
        String build = customBuild != null ? customBuild : type.getDefaultBuildCommand();
        String test = customTest != null ? customTest : type.getDefaultTestCommand();

        return RepositoryDtos.ProjectDetectionResult.builder()
                .detectedType(type)
                .suggestedInstallCommand(install)
                .suggestedBuildCommand(build)
                .suggestedTestCommand(test)
                .matchedIndicators(indicators)
                .confidence(confidence)
                .build();
    }
}
