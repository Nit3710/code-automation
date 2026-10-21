package com.platform.maintenance;

import com.platform.maintenance.dto.RepositoryDtos;
import com.platform.maintenance.model.enums.ProjectType;
import com.platform.maintenance.service.ProjectDetectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDetectorServiceTest {

    private final ProjectDetectorService detector = new ProjectDetectorService();

    @Test
    void testDetectJavaMavenProject(@TempDir Path tempDir) throws IOException {
        String pomXmlContent = """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pomXmlContent);

        RepositoryDtos.ProjectDetectionResult result = detector.detect(tempDir.toFile());

        assertEquals(ProjectType.JAVA_MAVEN, result.getDetectedType());
        assertTrue(result.getMatchedIndicators().contains("pom.xml"));
        assertTrue(result.getMatchedIndicators().contains("Spring Boot dependency"));
        assertEquals("mvn test", result.getSuggestedTestCommand());
        assertEquals("mvn clean compile", result.getSuggestedBuildCommand());
    }

    @Test
    void testDetectNextJsProject(@TempDir Path tempDir) throws IOException {
        String packageJsonContent = """
                {
                  "name": "my-next-app",
                  "version": "0.1.0",
                  "dependencies": {
                    "next": "14.1.0",
                    "react": "^18"
                  },
                  "scripts": {
                    "build": "next build",
                    "test": "jest"
                  }
                }
                """;
        Files.writeString(tempDir.resolve("package.json"), packageJsonContent);

        RepositoryDtos.ProjectDetectionResult result = detector.detect(tempDir.toFile());

        assertEquals(ProjectType.NEXT_JS, result.getDetectedType());
        assertTrue(result.getMatchedIndicators().contains("package.json"));
        assertTrue(result.getSuggestedTestCommand().contains("npm test"));
        assertEquals("npm run build", result.getSuggestedBuildCommand());
    }

    @Test
    void testDetectUnknownProject(@TempDir Path tempDir) {
        RepositoryDtos.ProjectDetectionResult result = detector.detect(tempDir.toFile());
        assertEquals(ProjectType.UNKNOWN, result.getDetectedType());
    }
}
