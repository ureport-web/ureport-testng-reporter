# ureport-testng-reporter

TestNG reporter that ships test results to [UReport](https://github.com/ureport). Supports Java 11+, Maven, and Gradle.

## Installation

### Maven

```xml
<dependency>
  <groupId>io.ureport</groupId>
  <artifactId>ureport-testng-reporter</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
testImplementation("io.ureport:ureport-testng-reporter:1.0.0")
```

## Configuration

### Register the listener

**testng.xml:**

```xml
<listeners>
  <listener class-name="io.ureport.testng.UReportListener"/>
</listeners>
```

**Maven Surefire plugin:**

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <properties>
      <property>
        <name>listener</name>
        <value>io.ureport.testng.UReportListener</value>
      </property>
    </properties>
    <systemPropertyVariables>
      <ureport.serverUrl>https://ureport.example.com</ureport.serverUrl>
      <ureport.apiToken>your-api-token</ureport.apiToken>
      <ureport.product>my-app</ureport.product>
      <ureport.type>unit</ureport.type>
    </systemPropertyVariables>
  </configuration>
</plugin>
```

**Gradle (Kotlin DSL):**

```kotlin
test {
  useTestNG { listeners.add("io.ureport.testng.UReportListener") }
  systemProperty("ureport.serverUrl", "https://ureport.example.com")
  systemProperty("ureport.apiToken", findProperty("ureportToken") ?: "")
  systemProperty("ureport.product", "my-app")
  systemProperty("ureport.type", "unit")
}
```

### Configuration properties

All properties can be set as system properties (`-Dureport.xxx`) or in `ureport.properties` in the working directory.

| Property | Required | Default | Description |
|---|---|---|---|
| `ureport.serverUrl` | yes | — | UReport server URL |
| `ureport.apiToken` | yes | — | Bearer API token |
| `ureport.product` | yes | — | Product name |
| `ureport.type` | yes | — | Build type (e.g. `unit`, `e2e`) |
| `ureport.buildNumber` | no | timestamp | Build number |
| `ureport.team` | no | — | Team label |
| `ureport.browser` | no | — | Browser label |
| `ureport.device` | no | — | Device label |
| `ureport.platform` | no | — | Platform label |
| `ureport.platform_version` | no | — | Platform version |
| `ureport.stage` | no | — | Stage (e.g. `staging`) |
| `ureport.version` | no | — | App version |
| `ureport.batchSize` | no | `50` | Tests per batch POST |
| `ureport.saveRelations` | no | `true` | Whether to save test relations |
| `ureport.outputFile` | no | — | Path to write JSON output file |
| `ureport.includeSteps` | no | `true` | Whether to include setup/body/teardown steps |

## Annotating tests

```java
import io.ureport.testng.UReport;
import io.ureport.testng.UReportSteps;

@Test
@UReport(uid = "TC-001", tags = {"smoke"}, components = {"Cart"}, teams = {"checkout"})
public void addToCartTest() throws Exception {
    UReportSteps.step("Open product page", () -> {
        // action
    });
    UReportSteps.step("Click add to cart", () -> {
        // action + assertions
    });
}
```

- `uid` — unique test case ID. Defaults to `ClassName#methodName` when not set.
- `tags` — merged with TestNG groups to form the final tags list.
- `components` / `teams` — labels for filtering in UReport.

## Steps

`UReportSteps.step()` records named steps with pass/fail status. Steps are thread-safe via `ThreadLocal` and safe for parallel test execution.

`@BeforeMethod` / `@AfterMethod` results are automatically captured as `setup` / `teardown` steps.

## Status mapping

| TestNG result | UReport status | `is_rerun` |
|---|---|---|
| PASS (no prior failure) | `PASS` | false |
| PASS (method also failed in this run) | `RERUN_PASS` | true |
| FAIL | `FAIL` | true if method also passed |
| SKIP | `SKIP` | false |

## Publishing to Maven Central

Use the `release` profile:

```sh
mvn deploy -Prelease
```

Requires GPG key and Maven Central credentials configured in `~/.m2/settings.xml`.
