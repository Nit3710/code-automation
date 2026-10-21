package com.platform.maintenance.model.enums;

public enum ProjectType {
    PYTHON("Python", "pytest || python -m unittest discover", "pip install -r requirements.txt"),
    JAVA_MAVEN("Java (Maven)", "mvn test", "mvn clean compile"),
    JAVA_GRADLE("Java (Gradle)", "./gradlew test", "./gradlew build -x test"),
    REACT_NPM("React (npm)", "npm test -- --watchAll=false", "npm run build"),
    NEXT_JS("Next.js (npm)", "npm test", "npm run build"),
    GENERIC_NODE("Node.js", "npm test", "npm run build"),
    CUSTOM("Custom", "echo 'ready'", "echo 'ready'"),
    UNKNOWN("Unknown", "", "");

    private final String displayName;
    private final String defaultTestCommand;
    private final String defaultBuildCommand;

    ProjectType(String displayName, String defaultTestCommand, String defaultBuildCommand) {
        this.displayName = displayName;
        this.defaultTestCommand = defaultTestCommand;
        this.defaultBuildCommand = defaultBuildCommand;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultTestCommand() {
        return defaultTestCommand;
    }

    public String getDefaultBuildCommand() {
        return defaultBuildCommand;
    }
}
