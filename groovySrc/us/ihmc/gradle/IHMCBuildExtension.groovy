package us.ihmc.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.java.JavaLibrary
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import us.ihmc.continuousIntegration.AgileTestingTools

//import java.nio.charset.StandardCharsets;
//import java.nio.file.FileVisitResult;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//
//import us.ihmc.commons.exception.DefaultExceptionHandler;
//import us.ihmc.commons.nio.BasicPathVisitor.PathType
//import us.ihmc.commons.nio.BasicPathVisitor
//import us.ihmc.commons.nio.PathTools
//import us.ihmc.commons.nio.FileTools
//import us.ihmc.commons.nio.WriteOption

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.attributes.Usage;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.plugins.*;
import org.gradle.api.internal.artifacts.publish.*;
import org.gradle.api.*;

import javax.inject.Inject;
import java.io.File;

/**
 * <p>
 * The {@code IHMCBuildExtension} provides some helper extensions to any project
 * that it is applied to exposed via the {@code ihmc} property on the project. Much
 * of this is specific to the way in which we organize our code and dependencies in
 * the IHMC Robotics Lab so may be of dubious usefulness to others.
 * </p>
 *
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
class IHMCBuildExtension
{
   private final Project containingProject;

   def IHMCBuildExtension(Project project)
   {
      this.containingProject = project
   }

   def void configureProjectForOpenRobotics(Project project)
   {
      applyJavaPlugins(project)

      project.group = "us.ihmc"
      project.version = '0.10.0'

      String publishMode = project.property("publishMode")
      if (publishMode == "SNAPSHOT")
      {
         project.version = getSnapshotVersion(project.version, project.property("buildNumber"))
      } else if (publishMode == "NIGHTLY")
      {
         project.version = getNightlyVersion(project.version)
      }

      project.testSuites {
         bambooPlanKeys = ['LIBS-UI2', 'LIBS-FAST2', 'LIBS-FLAKY2', 'LIBS-SLOW2', 'LIBS-VIDEO2', 'LIBS-INDEVELOPMENT2']
      }

      project.ext.vcsUrl = "https://github.com/ihmcrobotics/ihmc-open-robotics-software"
      project.ext.licenseURL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
      project.ext.licenseName = "Apache License, Version 2.0"
      project.ext.companyName = "IHMC"
      project.ext.maintainer = "IHMC Gradle Build Script"

      addIHMCMavenRepositories(project)
      addThirdPartyMavenRepositories(project)
      addPublicMavenRepositories(project)
      addLocalMavenRepository(project)

      //setupAggressiveResolutionStrategy(project)
      setupJavaSourceSets(project)

      setupCommonJARConfiguration(project)

      if (publishMode == "SNAPSHOT")
      {
         declareArtifactory(project, "snapshots")
      } else if (publishMode == "NIGHTLY")
      {
         declareArtifactory(project, "nightlies")
      } else if (publishMode == "STABLE")
      {
         declareBintray(project)
      }

      declarePublication(project, project.name, project.configurations.compile, project.sourceSets.main)
      declarePublication(project, project.name + '-test', project.configurations.testCompile, project.sourceSets.test, project.name)
   }

   def void applyJavaPlugins(Project project)
   {
      project.apply plugin: 'java'
      project.apply plugin: 'eclipse'
      project.apply plugin: 'idea'
      project.apply plugin: 'maven-publish'

      project.sourceCompatibility = 1.8
      project.targetCompatibility = 1.8
   }

   def String getSnapshotVersion(String version, buildNumber)
   {
      return version + "-SNAPSHOT-" + buildNumber;
   }

   def String getNightlyVersion(String version)
   {
      return version + "-NIGHTLY-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
   }

   def void setupJavaSourceSets(Project project)
   {
      project.sourceSets {
         main {
            java {
               srcDirs = ['src']
            }

            resources {
               srcDirs = ['src', 'resources']
            }
         }

         test {
            java {
               srcDirs = ['test']
            }

            resources {
               srcDirs = ['testResources']
            }
         }
      }

      JavaPluginConvention convention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
      ConfigurationContainer configurations = project.getConfigurations();
      SourceSet sourceSet = convention.getSourceSets().getByName("test");

      Configuration testConfiguration = configurations.maybeCreate("test");
      testConfiguration.setVisible(false);
      testConfiguration.setDescription("Test dependencies for " + sourceSet + ".");
      testConfiguration.setCanBeResolved(false);
      testConfiguration.setCanBeConsumed(false);

      Configuration testCompileConfiguration = configurations.getByName("testCompile");
      testCompileConfiguration.extendsFrom(testConfiguration);

      final JavaCompile javaTestCompile = (JavaCompile) project.getTasks().getByPath("compileTestJava");

      // Define a classes variant to use for compilation
      ConfigurationPublications publications = testCompileConfiguration.getOutgoing();
      ConfigurationVariant variant = publications.getVariants().create("classes");
      variant.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, "java-test-classes"));
      variant.artifact(new IntermediateArtifact("java-classes-directory", javaTestCompile) {
         @Override
         public File getFile()
         {
            return javaTestCompile.getDestinationDir();
         }
      });

//      Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
//      implementationConfiguration.extendsFrom(testConfiguration);

//      Configuration compileConfiguration = configurations.getByName(sourceSet.getCompileConfigurationName());
//      testConfiguration.extendsFrom(compileConfiguration);
   }

   abstract static class IntermediateArtifact extends AbstractPublishArtifact
   {
      private final String type;

      public IntermediateArtifact(String type, Task task)
      {
         super(task);
         this.type = type;
      }

      @Override
      public String getName()
      {
         return getFile().getName();
      }

      @Override
      public String getExtension()
      {
         return "";
      }

      @Override
      public String getType()
      {
         return type;
      }

      @Nullable
      @Override
      public String getClassifier()
      {
         return null;
      }

      @Override
      public Date getDate()
      {
         return null;
      }
   }

   def void addIHMCMavenRepositories(Project project)
   {
      project.repositories {
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-vendor"
         }
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-release"
         }
         maven {
            url "https://artifactory.ihmc.us/artifactory/releases/"
         }
         maven {
            url "https://artifactory.ihmc.us/artifactory/thirdparty/"
         }
         maven {
            url "http://artifactory.ihmc.us/artifactory/snapshots/"
         }
      }
   }

   def void addThirdPartyMavenRepositories(Project project)
   {
      project.repositories {
         maven {
            url "http://clojars.org/repo/"
         }
         maven {
            url "https://github.com/rosjava/rosjava_mvn_repo/raw/master"
         }
         maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
         }
      }
   }

   def void addPublicMavenRepositories(Project project)
   {
      project.repositories {
         jcenter()
         mavenCentral()
      }
   }

   def void addLocalMavenRepository(Project project)
   {
      project.repositories {
         mavenLocal()
      }
   }

   def String getBuildVersion(String groupId, String artifactId, String dependencyMode)
   {
      def username = containingProject.property("artifactoryUsername")
      def password = containingProject.property("artifactoryPassword")

      String buildVersion = "error";
      if (dependencyMode.startsWith("STABLE"))
      {
         int firstDash = dependencyMode.indexOf("-");
         if (firstDash > 0)
         {
            buildVersion = dependencyMode.substring(firstDash + 1);
         }
      } else
      {
         try
         {
            ArtifactoryClientBuilder builder = ArtifactoryClientBuilder.create();
            builder.setUrl("https://artifactory.ihmc.us/artifactory");
            builder.setUsername(username);
            builder.setPassword(password);
            Artifactory artifactory = builder.build();
            List<RepoPath> snapshots = artifactory.searches().artifactsByGavc().repositories("snapshots").groupId("us.ihmc").artifactId(artifactId).doSearch();

            String latestVersion = null;
            int latestBuildNumber = -1;
            for (RepoPath repoPath : snapshots)
            {
               if (repoPath.getItemPath().endsWith(".pom") || repoPath.getItemPath().endsWith("sources.jar"))
               {
                  continue;
               }

               if (!repoPath.getItemPath().contains(dependencyMode))
               {
                  continue;
               }

               String version = itemPathToVersion(repoPath.getItemPath(), artifactId);
               int buildNumber = buildNumber(version);

               // Found exact nightly
               if (version.endsWith(dependencyMode))
               {
                  buildVersion = itemPathToVersion(repoPath.getItemPath(), artifactId);
               }

               if (latestVersion == null)
               {
                  latestVersion = version;
                  latestBuildNumber = buildNumber;
               } else if (buildNumber > latestBuildNumber)
               {
                  latestVersion = version;
                  latestBuildNumber = buildNumber;
               }
            }

            buildVersion = latestVersion;
         }
         catch (Exception exception)
         {
            System.out.println("Artifactory could not be reached, reverting to latest.");
            buildVersion = "error";
         }
      }

      if (buildVersion == null) buildVersion = "error"

      return groupId + ":" + artifactId + ":" + buildVersion;
   }

   private int buildNumber(String version)
   {
      return Integer.parseInt(version.split("-")[2]);
   }

   private String itemPathToVersion(String itemPath, String artifactId)
   {
      String[] split = itemPath.split("/");
      String artifact = split[split.length - 1];
      String withoutDotJar = artifact.split("\\.jar")[0];
      String version = withoutDotJar.substring(artifactId.length() + 1);

      return version;
   }

   def void setupCommonJARConfiguration(Project project)
   {
      setupCommonJARConfiguration(project, project.maintainer, project.companyName, project.licenseURL)
   }

   def void setupCommonJARConfiguration(Project project, String maintainer, String companyName, String licenseURL)
   {
      project.jar {
         manifest {
            attributes(
                  'Created-By': maintainer,
                  'Implementation-Title': project.name,
                  'Implementation-Version': project.version,
                  'Implementation-Vendor': companyName,

                  'Bundle-Name': project.name,
                  'Bundle-Version': project.version,
                  'Bundle-License': licenseURL,
                  'Bundle-Vendor': companyName)
         }
      }
   }

   def void declareArtifactory(Project project, String repository)
   {
      project.publishing.repositories {
         maven {
            name 'Artifactory' + repository.capitalize()
            url 'https://artifactory.ihmc.us/artifactory/' + repository
            credentials {
               username = project.property("artifactoryUsername")
               password = project.property("artifactoryPassword")
            }
         }
      }
   }

   def void declareBintray(Project project)
   {
      project.publishing.repositories {
         maven {
            name 'BintrayRelease'
            url 'https://api.bintray.com/maven/ihmcrobotics/maven-release/' + project.name
            credentials {
               username = project.property("bintray_user")
               password = project.property("bintray_key")
            }
         }
      }
   }

   def void declarePublication(Project project, String artifactName, Configuration configuration,
                               SourceSet sourceSet, String... internalDependencies)
   {
      project.publishing.publications.create(sourceSet.getName().capitalize(), MavenPublication) {
         groupId project.group
         artifactId artifactName
         version project.version

         pom.withXml {
            def dependenciesNode = asNode().appendNode('dependencies')

            internalDependencies.each {
               def internalDependency = dependenciesNode.appendNode('dependency')
               internalDependency.appendNode('groupId', project.group)
               internalDependency.appendNode('artifactId', it)
               internalDependency.appendNode('version', project.version)
            }

            configuration.allDependencies.each {
               if (it.name != "unspecified")
               {
                  def dependencyNode = dependenciesNode.appendNode('dependency')
                  dependencyNode.appendNode('groupId', it.group)
                  dependencyNode.appendNode('artifactId', it.name)
                  dependencyNode.appendNode('version', it.version)
               }
            }

            asNode().children().last() + {
               resolveStrategy = DELEGATE_FIRST
               name project.name
               url project.ext.vcsUrl
               licenses {
                  license {
                     name project.ext.licenseName
                     url project.ext.licenseURL
                     distribution "repo"
                  }
               }
            }
         }

         artifact project.task(type: Jar, sourceSet.getName() + "ClassesJar", {
            from sourceSet.output
         })

         artifact project.task(type: Jar, sourceSet.getName() + "SourcesJar", {
            from sourceSet.allJava
            classifier 'sources'
         })
      }
   }

   def void setupGroovySourceSets(Project project)
   {
      project.sourceSets {
         main {
            groovy {
               srcDirs = ['groovySrc']
            }

            resources {
               srcDirs = ['src', 'resources']
            }
         }

         test {
            groovy {
               srcDirs = ['groovyTest']
            }

            resources {
               srcDirs = ['testResources']
            }
         }
      }
   }

   def void setupAggressiveResolutionStrategy()
   {
      containingProject.configurations.all {
         resolutionStrategy {
            //failOnVersionConflict()
            preferProjectModules()

            cacheDynamicVersionsFor 0, 'seconds'
            cacheChangingModulesFor 0, 'seconds'
         }
      }
   }

   def void setupAggressiveResolutionStrategy(Project project)
   {
      project.configurations.all {
         resolutionStrategy {
            //failOnVersionConflict()
            preferProjectModules()

            cacheDynamicVersionsFor 0, 'seconds'
            cacheChangingModulesFor 0, 'seconds'
         }
      }
   }

   def String convertJobNameToHyphenatedName(String jobName)
   {
      return AgileTestingTools.pascalCasedToHyphenatedWithoutJob(jobName)
   }

//   def void convertDirectoryLineEndingsFromDosToUnix(Project project, String pathAsString)
//   {
//      Path directory = project.projectDir.toPath().resolve(pathAsString)
//
//      println "Converting line endings to Unix: " + directory
//      PathTools.walkRecursively(directory, new BasicPathVisitor()
//      {
//         @Override
//         public FileVisitResult visitPath(Path path, PathType pathType)
//         {
//            if (pathType.equals(PathType.FILE))
//            {
//               byte[] fileAsBytes = FileTools.readAllBytes(path, DefaultExceptionHandler.PRINT_STACKTRACE);
//               String fileAsString = new String(fileAsBytes, StandardCharsets.UTF_8);
//               fileAsString.replaceAll("\r\n", "\n");
//               FileTools.write(path, fileAsString.getBytes(), WriteOption.TRUNCATE, DefaultExceptionHandler.PRINT_STACKTRACE);
//            }
//
//            return FileVisitResult.CONTINUE;
//         }
//      });
//   }
}
