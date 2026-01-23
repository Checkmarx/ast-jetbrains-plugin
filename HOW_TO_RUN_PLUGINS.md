# How to Run Checkmarx IntelliJ Plugins

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Building the Plugins](#building-the-plugins)
3. [Running Plugins in Development Mode](#running-plugins-in-development-mode)
4. [Running Tests](#running-tests)
5. [Installing Plugins Locally](#installing-plugins-locally)
6. [Debugging](#debugging)
7. [Common Issues and Solutions](#common-issues-and-solutions)
8. [Environment Variables](#environment-variables)

---

## 1. Prerequisites

### 1.1 Required Software

**Java Development Kit (JDK):**
- JDK 11 or higher
- Verify installation:
  ```bash
  java -version
  ```

**Gradle:**
- Gradle 7.x or higher (included via Gradle Wrapper)
- Verify installation:
  ```bash
  ./gradlew --version
  ```

**IntelliJ IDEA:**
- IntelliJ IDEA 2022.2.1 or higher
- Community or Ultimate Edition

### 1.2 Environment Setup

**Clone the Repository:**
```bash
git clone <repository-url>
cd ast-jetbrains-plugin
```

**Verify Module Structure:**
```
ast-jetbrains-plugin/
├── common-lib/
├── plugin-core/
├── plugin-devassist/
├── settings.gradle
└── build.gradle
```

---

## 2. Building the Plugins

### 2.1 Build All Modules

**Build entire project:**
```bash
./gradlew build
```

**Expected Output:**
```
> Task :common-lib:build
> Task :plugin-core:build
> Task :plugin-devassist:build

BUILD SUCCESSFUL in Xs
```

### 2.2 Build Individual Modules

**Build common-lib only:**
```bash
./gradlew :common-lib:build
```

**Build plugin-core only:**
```bash
./gradlew :plugin-core:build
```

**Build plugin-devassist only:**
```bash
./gradlew :plugin-devassist:build
```

### 2.3 Clean Build

**Clean all modules:**
```bash
./gradlew clean build
```

**Clean specific module:**
```bash
./gradlew :plugin-core:clean :plugin-core:build
```

---

## 3. Running Plugins in Development Mode

### 3.1 Run Core Plugin (Checkmarx)

**Using Gradle:**
```bash
./gradlew :plugin-core:runIde
```

**What happens:**
- Gradle downloads IntelliJ IDEA 2022.2.1
- Starts IntelliJ with the Checkmarx plugin installed
- Plugin is loaded in development mode
- Changes to code require rebuild and restart

**Expected Output:**
```
> Task :plugin-core:runIde
Starting IntelliJ IDEA...
```

### 3.2 Run DevAssist Plugin (Checkmarx One Assist)

**Using Gradle:**
```bash
./gradlew :plugin-devassist:runIde
```

**What happens:**
- Starts IntelliJ with both Checkmarx and Checkmarx One Assist plugins
- DevAssist plugin depends on Core plugin, so both are loaded
- Full functionality available

### 3.3 Run with Custom IntelliJ Version

**Edit build.gradle:**
```groovy
intellij {
    version = '2023.1'  // Change version here
    updateSinceUntilBuild = false
}
```

**Then run:**

1. Open IntelliJ IDEA
2. Go to **File → Settings → Plugins** (Windows/Linux) or **IntelliJ IDEA → Preferences → Plugins** (macOS)
3. Click the **⚙️ (gear icon) → Install Plugin from Disk...**
4. Navigate to `plugin-core/build/distributions/`
5. Select `Checkmarx-<version>.zip`
6. Click **OK**
7. Restart IntelliJ IDEA
8. Repeat steps 3-7 for `plugin-devassist/build/distributions/Checkmarx One Assist-<version>.zip`

**Method 2: Via Command Line**
```bash
# Copy plugin to IntelliJ plugins directory
# Windows
copy plugin-core\build\distributions\Checkmarx-*.zip %USERPROFILE%\.IntelliJIdea2022.2\config\plugins\

# macOS
cp plugin-core/build/distributions/Checkmarx-*.zip ~/Library/Application\ Support/JetBrains/IntelliJIdea2022.2/plugins/

# Linux
cp plugin-core/build/distributions/Checkmarx-*.zip ~/.IntelliJIdea2022.2/config/plugins/
```

### 5.3 Verify Installation

1. Restart IntelliJ IDEA
2. Go to **File → Settings → Plugins**
3. Search for "Checkmarx"
4. Verify both plugins are listed:
   - ✅ Checkmarx
   - ✅ Checkmarx One Assist

---

## 6. Debugging

### 6.1 Debug Core Plugin

**Start in debug mode:**
```bash
./gradlew :plugin-core:runIde --debug-jvm
```

**Expected Output:**
```
Listening for transport dt_socket at address: 5005
```

**Attach debugger:**
1. Open the project in IntelliJ IDEA
2. Go to **Run → Edit Configurations**
3. Click **+** → **Remote JVM Debug**
4. Set **Host:** `localhost`
5. Set **Port:** `5005`
6. Click **OK**
7. Click **Debug** button
8. Set breakpoints in your code
9. Trigger the functionality in the running IDE

### 6.2 Debug DevAssist Plugin

**Start in debug mode:**
```bash
./gradlew :plugin-devassist:runIde --debug-jvm
```

**Follow same steps as 6.1 to attach debugger**

### 6.3 Debug Tests

**Run tests in debug mode:**
```bash
./gradlew :plugin-core:test --debug-jvm
```

**Or use IntelliJ's built-in test runner:**
1. Right-click on test class
2. Select **Debug 'TestClassName'**

### 6.4 Enable Plugin Logging

**Add to IntelliJ IDEA:**
1. Go to **Help → Diagnostic Tools → Debug Log Settings**
2. Add:
   ```
   #com.checkmarx.intellij
   ```
3. Click **OK**
4. View logs at **Help → Show Log in Explorer/Finder**

**Or edit log configuration file:**
```bash
# Windows
%USERPROFILE%\.IntelliJIdea2022.2\system\log\idea.log

# macOS
~/Library/Logs/JetBrains/IntelliJIdea2022.2/idea.log

# Linux
~/.IntelliJIdea2022.2/system/log/idea.log
```

---

## 7. Common Issues and Solutions

### 7.1 Build Failures

**Issue: "Could not resolve dependencies"**

**Solution:**
```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Or delete .gradle directory
rm -rf .gradle
./gradlew build
```

**Issue: "Compilation failed; see the compiler error output for details"**

**Solution:**
```bash
# Check Java version
java -version

# Ensure JDK 11 is being used
./gradlew -version

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/jdk-11
```

**Issue: "UTF-8 encoding errors"**

**Solution:**
Already configured in build.gradle:
```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

### 7.2 Runtime Issues

**Issue: "Plugin failed to load"**

**Solution:**
1. Check IntelliJ version compatibility (2022.2.1+)
2. Verify plugin.xml configuration
3. Check logs for errors:
   ```bash
   # View logs
   tail -f ~/.IntelliJIdea2022.2/system/log/idea.log
   ```

**Issue: "DevAssist features not working"**

**Solution:**
1. Verify both plugins are installed:
   - Checkmarx (plugin-core)
   - Checkmarx One Assist (plugin-devassist)
2. DevAssist depends on Core plugin
3. Restart IntelliJ after installing both

**Issue: "ClassNotFoundException for DevAssist classes"**

**Solution:**
This is expected if only Core plugin is installed. DevAssist integration is optional and uses reflection.

### 7.3 Test Failures

**Issue: "Integration tests failing"**

**Solution:**
Expected behavior. Integration tests require:
- Valid Checkmarx credentials
- API access
- Network connectivity

**Set environment variables for integration tests:**
```bash
export CX_BASE_URI=https://your-instance.checkmarx.net
export CX_TENANT=your-tenant
export CX_API_KEY=your-api-key

./gradlew test
```

**Issue: "mockito-junit-jupiter not found"**

**Solution:**
Already added to build.gradle:
```groovy
testImplementation 'org.mockito:mockito-junit-jupiter:5.0.0'
```

### 7.4 Performance Issues

**Issue: "Gradle build is slow"**

**Solution:**
```bash
# Enable Gradle daemon
echo "org.gradle.daemon=true" >> gradle.properties

# Increase memory
echo "org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m" >> gradle.properties

# Enable parallel builds
echo "org.gradle.parallel=true" >> gradle.properties
```

**Issue: "IntelliJ IDE runs slowly with plugin"**

**Solution:**
1. Increase IntelliJ memory:
   - **Help → Edit Custom VM Options**
   - Add: `-Xmx2048m`
2. Disable unnecessary plugins
3. Check for infinite loops or memory leaks in plugin code

---

## 8. Environment Variables

### 8.1 Build-Time Variables

**RELEASE_VERSION:**
```bash
# Set plugin version
export RELEASE_VERSION=1.0.0
./gradlew build
```

**JAVA_WRAPPER_VERSION:**
```bash
# Set AST CLI Java Wrapper version
export JAVA_WRAPPER_VERSION=2.4.17.6
./gradlew build
```

### 8.2 Runtime Variables

**CX_BASE_URI:**
```bash
# Checkmarx server URL
export CX_BASE_URI=https://your-instance.checkmarx.net
```

**CX_TENANT:**
```bash
# Checkmarx tenant name
export CX_TENANT=your-tenant
```

**CX_API_KEY:**
```bash
# Checkmarx API key
export CX_API_KEY=your-api-key
```

### 8.3 Test Variables

**Set all test variables:**
```bash
# Windows (PowerShell)
$env:CX_BASE_URI="https://your-instance.checkmarx.net"
$env:CX_TENANT="your-tenant"
$env:CX_API_KEY="your-api-key"
./gradlew test

# macOS/Linux (Bash)
export CX_BASE_URI=https://your-instance.checkmarx.net
export CX_TENANT=your-tenant
export CX_API_KEY=your-api-key
./gradlew test
```

---

## 9. Quick Reference Commands

### 9.1 Development Workflow

```bash
# 1. Clean build
./gradlew clean build

# 2. Run Core plugin
./gradlew :plugin-core:runIde

# 3. Run DevAssist plugin
./gradlew :plugin-devassist:runIde

# 4. Run tests
./gradlew test

# 5. Build distributions
./gradlew :plugin-core:buildPlugin
./gradlew :plugin-devassist:buildPlugin
```

### 9.2 Testing Workflow

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :plugin-core:test
./gradlew :plugin-devassist:test

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test
./gradlew :plugin-core:test --tests "ResultNodeTest"

# Run tests in debug mode
./gradlew :plugin-core:test --debug-jvm
```

### 9.3 Debugging Workflow

```bash
# 1. Start IDE in debug mode
./gradlew :plugin-core:runIde --debug-jvm

# 2. Attach debugger from IntelliJ
# Run → Edit Configurations → + → Remote JVM Debug
# Host: localhost, Port: 5005

# 3. Set breakpoints and debug
```

---

## 10. IDE-Specific Instructions

### 10.1 IntelliJ IDEA

**Import Project:**
1. Open IntelliJ IDEA
2. **File → Open**
3. Select `ast-jetbrains-plugin` directory
4. Click **OK**
5. IntelliJ will auto-detect Gradle project
6. Wait for Gradle sync to complete

**Run Configuration:**
1. **Run → Edit Configurations**
2. Click **+** → **Gradle**
3. Name: "Run Core Plugin"
4. Gradle project: `:plugin-core`
5. Tasks: `runIde`
6. Click **OK**

**Repeat for DevAssist:**
1. Name: "Run DevAssist Plugin"
2. Gradle project: `:plugin-devassist`
3. Tasks: `runIde`

### 10.2 VS Code

**Install Extensions:**
- Gradle for Java
- Java Extension Pack

**Run Tasks:**
1. Open Command Palette (Ctrl+Shift+P)
2. Type "Gradle: Run Task"
3. Select task:
   - `:plugin-core:runIde`
   - `:plugin-devassist:runIde`
   - `test`

### 10.3 Command Line Only

**All operations via Gradle Wrapper:**
```bash
# Build
./gradlew build

# Run
./gradlew :plugin-core:runIde

# Test
./gradlew test

# Package
./gradlew :plugin-core:buildPlugin
```

---

## 11. Continuous Integration

### 11.1 GitHub Actions Example

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2

    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew build

    - name: Run tests
      run: ./gradlew test

    - name: Build plugins
      run: |
        ./gradlew :plugin-core:buildPlugin
        ./gradlew :plugin-devassist:buildPlugin

    - name: Upload artifacts
      uses: actions/upload-artifact@v2
      with:
        name: plugins
        path: |
          plugin-core/build/distributions/*.zip
          plugin-devassist/build/distributions/*.zip
```

### 11.2 Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK 11'
    }

    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
        }

        stage('Test') {
            steps {
                sh './gradlew test'
            }
        }

        stage('Package') {
            steps {
                sh './gradlew :plugin-core:buildPlugin'
                sh './gradlew :plugin-devassist:buildPlugin'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: '**/build/distributions/*.zip'
            }
        }
    }
}
```

---

## 12. Troubleshooting Checklist

### Before Running:
- [ ] JDK 11+ installed
- [ ] JAVA_HOME set correctly
- [ ] Gradle wrapper executable (`./gradlew`)
- [ ] All modules present (common-lib, plugin-core, plugin-devassist)

### Build Issues:
- [ ] Run `./gradlew clean`
- [ ] Run `./gradlew --refresh-dependencies`
- [ ] Check Java version: `java -version`
- [ ] Check Gradle version: `./gradlew --version`
- [ ] Delete `.gradle` directory and rebuild

### Runtime Issues:
- [ ] IntelliJ version 2022.2.1+
- [ ] Both plugins installed (for DevAssist features)
- [ ] Check logs: `Help → Show Log`
- [ ] Restart IntelliJ IDEA
- [ ] Reinstall plugins

### Test Issues:
- [ ] Unit tests should pass
- [ ] Integration tests require credentials
- [ ] Set environment variables for integration tests
- [ ] Check test reports in `build/reports/tests/`

---

## 13. Additional Resources

### Documentation:
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin)
- [Module Split Documentation](MODULE_SPLIT_DOCUMENTATION.md)

### Support:
- Check logs: `Help → Show Log in Explorer/Finder`
- Enable debug logging: `Help → Diagnostic Tools → Debug Log Settings`
- Report issues to development team

---

**Document Version:** 1.0
**Last Updated:** January 21, 2026
**Author:** Augment Agent
**Status:** Final

---

## 4. Running Tests

### 4.1 Run All Tests

**Run tests for all modules:**
```bash
./gradlew test
```

**Expected Output:**
```
> Task :common-lib:test
> Task :plugin-core:test
> Task :plugin-devassist:test

122 tests completed, 21 failed
```

**Note:** Integration test failures are expected without proper credentials.

### 4.2 Run Tests for Specific Module

**Run plugin-core tests:**
```bash
./gradlew :plugin-core:test
```

**Run plugin-devassist tests:**
```bash
./gradlew :plugin-devassist:test
```

### 4.3 Run Specific Test Class

**Run single test class:**
```bash
./gradlew :plugin-core:test --tests "com.checkmarx.intellij.tool.window.ResultNodeTest"
```

### 4.4 Run Tests with Coverage

**Generate coverage report:**
```bash
./gradlew test jacocoTestReport
```

**View coverage report:**
```
plugin-core/build/reports/jacoco/test/html/index.html
plugin-devassist/build/reports/jacoco/test/html/index.html
```

### 4.5 View Test Reports

**Test reports location:**
```
plugin-core/build/reports/tests/test/index.html
plugin-devassist/build/reports/tests/test/index.html
```

**Open in browser:**
```bash
# Windows
start plugin-core/build/reports/tests/test/index.html

# macOS
open plugin-core/build/reports/tests/test/index.html

# Linux
xdg-open plugin-core/build/reports/tests/test/index.html
```

---

## 5. Installing Plugins Locally

### 5.1 Build Plugin Distribution

**Build plugin-core distribution:**
```bash
./gradlew :plugin-core:buildPlugin
```

**Output location:**
```
plugin-core/build/distributions/Checkmarx-<version>.zip
```

**Build plugin-devassist distribution:**
```bash
./gradlew :plugin-devassist:buildPlugin
```

**Output location:**
```
plugin-devassist/build/distributions/Checkmarx One Assist-<version>.zip
```

### 5.2 Install in IntelliJ IDEA

**Method 1: Via Settings UI**

