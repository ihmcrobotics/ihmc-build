# IHMC CI
# Functionality now included in [ihmc-build](https://github.com/ihmcrobotics/ihmc-build)

Gradle plugin for running groups of tests with varied runtime requirements.

## Features

- Easily define and run categories of tests
- Uses the standard `gradle test` task
- IDE support for running tests in parallel
- Built-in allocation testing support for realtime safety
- Built on JUnit 5, Gradle, and JVM
- Load resources using file APIs with the `resource.dir` JVM property
- `runningOnCIServer` boolean JVM property to improve running tests locally
- Support for projects using [ihmc-build](https://github.com/ihmcrobotics/ihmc-build) plugin
- Generate empty test results when no tests are present to avoid false negative builds
- Provide list of tests and tags to be run in console output i.e. no uncertainty about which tests are run
- Set up full lifecycle logging for tests in --info log level i.e. started, passed, failed, skipped

## Download

```kotlin
plugins {
   id("us.ihmc.ihmc-build") version "0.29.3"
   id("us.ihmc.ihmc-ci") version "7.7"
}
```

## Testing in the IDE

To run tests in parallel in your IDE (useful for visualizers), add the following JVM properties to your run configuration:

```
-Djunit.jupiter.execution.parallel.enabled=true
-Djunit.jupiter.execution.parallel.mode.default=concurrent
```

See more at https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution

## Gradle

### User Guide

This plugin defines a concept of `categories`. Categories are communicated via the `category` Gradle
property (i.e. `gradle test -Pcategory=fast`)and are used to set up a test process to run tests based on tags, parallel
execution settings, and JVM arguments.

#### Custom categories

In your project's `build.gradle.kts` (Kotlin):
```kotlin
categories.configure("scs-slow")
{
   forkEvery = 0   // default: 0
   maxParallelForks = 1   // default: 1
   includeTags += "scs-slow"   // default: all tests, fast tests, or category name
   jvmProperties += "some.arg" to "value"   // default: empty List
   jvmArguments += "-Dsome.arg=value"   // default: empty List
   minHeapSizeGB = 1   // default: 1
   maxHeapSizeGB = 8   // default: 4
}
```

or in `build.gradle` (Groovy):
```groovy
def gui = categories.configure("gui")
gui.forkEvery = 0
gui.maxParallelForks = 1
gui.minHeapSizeGB = 6
gui.maxHeapSizeGB = 8
 
def video = categories.configure("video")
video.forkEvery = 0  // forkEvery
video.maxParallelForks = 1        // maxParallelForks
video.minHeapSizeGB = 6
video.maxHeapSizeGB = 8
 
def scsAllocation = categories.configure("scs-allocation")
scsAllocation.forkEvery = 0  // forkEvery
scsAllocation.maxParallelForks = 1        // maxParallelForks
scsAllocation.jvmArguments.add("allocationAgent")
scsAllocation.minHeapSizeGB = 6
scsAllocation.maxHeapSizeGB = 8
```

Special JVM argument accessors:

- "allocationAgent" - Find location of `-javaagent:[..]java-allocation-instrumenter[..].jar`

The plugin will do a few other things too:

- If `-PrunningOnCIServer=true`, set `-DrunningOnCIServer=true`.
- Pass `-Dresources.dir` that points to your resources folder on disk.
- Pass `-ea` JVM argument to enable JVM assertions

#### Examples

```bash
$ gradle test -Pcategory=fast   // run fast tests
```

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Test
public void fastTest() { ... }   // runs in fast category

@Tag("allocation")
@Test
public void allocationTest() { ... }   // runs in allocation category
```
