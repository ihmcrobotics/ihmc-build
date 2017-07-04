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
         project.version = getSnapshotVersion(project.version, project.property("bambooBuildNumber"))
      } else if (publishMode == "NIGHTLY")
      {
         project.version = getNightlyVersion(project.version)
      }

      project.ext.vcsUrl = "https://github.com/ihmcrobotics/ihmc-open-robotics-software"
      project.ext.licenseURL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
      project.ext.licenseName = "Apache License, Version 2.0"
      project.ext.companyName = "IHMC"
      project.ext.maintainer = "IHMC Gradle Build Script"

      setupCommonArtifactProxies(project)

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
   }

   def void setupCommonArtifactProxies(Project project)
   {
      project.repositories {
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-vendor"
         }
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-release"
         }
         maven {
            url "http://clojars.org/repo/"
         }
         maven {
            url "https://github.com/rosjava/rosjava_mvn_repo/raw/master"
         }
         maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
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
         mavenLocal()
         jcenter()
         mavenCentral()
      }
   }

   def String getDynamicVersion(String groupId, String artifactId, String dependencyMode)
   {
      def username = containingProject.property("artifactoryUsername")
      def password = containingProject.property("artifactoryPassword")

      String dynamicVersion = "+";
      if (dependencyMode.startsWith("STABLE"))
      {
         int firstDash = dependencyMode.indexOf("-");
         if (firstDash > 0)
         {
            dynamicVersion = dependencyMode.substring(firstDash + 1);
         } else
         {
            dynamicVersion = "+";
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
                  dynamicVersion = itemPathToVersion(repoPath.getItemPath(), artifactId);
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

            dynamicVersion = latestVersion;
         }
         catch (Exception exception)
         {
            System.out.println("Artifactory could not be reached, reverting to latest.");
            dynamicVersion = "+";
         }
      }

      if (dynamicVersion == null) dynamicVersion = "+"

      return groupId + ":" + artifactId + ":" + dynamicVersion;
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

   @Deprecated
   /**
    * <p>
    * Set up a closure containing all of the commonly used artifact repos and
    * proxies common across IHMC Robotics software.
    * </p>
    *
    * @return a {@link Closure that generates all of the "default" {@link org.gradle.api.artifacts.repositories.ArtifactRepository}s we typically use
    */
   def Closure setupCommonArtifactProxies()
   {
      return {
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-vendor"
         }
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-release"
         }
         maven {
            url "http://clojars.org/repo/"
         }
         maven {
            url "https://github.com/rosjava/rosjava_mvn_repo/raw/master"
         }
         maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
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

   @Deprecated
   def void setupSourceSetStructure()
   {
      setupJavaSourceSets(containingProject)

      if (containingProject.plugins.hasPlugin(GroovyPlugin))
      {
         setupGroovySourceSets(containingProject)
      }
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

   @Deprecated
   def void setupCommonPublishingConfiguration(Project project)
   {
      project.publishing {
         publications {
            mavenJava(MavenPublication) {
               groupId project.group
               artifactId project.name
               version project.version
               from project.components.java

               pom.withXml {
                  asNode().children().last() + {
                     resolveStrategy = DELEGATE_FIRST
                     name project.name
                     url project.ext.vcsUrl
                     licenses {
                        license {
                           name project.ext.licenseName
                           url project.ext.licenseURL
                           distribution 'repo'
                        }
                     }
                  }
               }

               artifact project.task(type: Jar, "sourceJar", {
                  from project.sourceSets.main.allJava
                  classifier 'sources'
               })

               artifact project.task(type: Jar, dependsOn: project.getTasks().getByName("compileTestJava"), "testJar", {
                  println project.components
                  from new JavaLibrary()
                  classifier 'test'
               })
            }
         }
      }
   }

   @Deprecated
   def void setupArtifactoryPublishingConfiguration(Project project)
   {
      project.artifactory {
         contextUrl = 'https://artifactory.ihmc.us/artifactory'

         publish {
            repository {
               repoKey = project.ext.artifactoryRepo
               username = project.property("artifactoryUsername")
               password = project.property("artifactoryPassword")
            }
            defaults {
               publications('mavenJava')
            }
         }

         resolve {
            repository {
               repoKey = 'libs-releases'
            }
         }
      }
   }

   @Deprecated
   def void setupBintrayPublishingConfiguration(Project project)
   {
      project.bintray {
         user = project.property("bintray_user")
         key = project.property("bintray_key")

         dryRun = project.ext.bintrayDryRun
         publish = false

         publications = ['mavenJava']

         pkg {
            repo = project.ext.bintrayRepo
            name = project.name
            userOrg = project.ext.bintrayOrg
            desc = project.name

            websiteUrl = project.ext.vcsUrl
            issueTrackerUrl = project.ext.vcsUrl + '/issues'
            vcsUrl = project.ext.vcsUrl + '.git'

            licenses = [project.ext.bintrayLicenseName]
            labels = ['ihmc', 'java']
            publicDownloadNumbers = true

            version {
               name = project.version
               desc = project.name + ' v' + project.version
               released = new Date()
               vcsTag = 'v' + project.version
            }
         }
      }
   }

   @Deprecated
   /**
    * <p>
    * This method is used to naïvely look up a Project in a multi-project build by its name,
    * irrespective of the hierarchy that contains it. The primary use case for this is to allow for
    * configuration-independent lookup of local source depenencies that can either be part of a standalone
    * multi-project build or as part of a several-level nested developer workspace. E.g., source dependencies
    * in the project IHMCOpenRoboticsSoftware can be identified as ":ProjectName" if being built within IHMCOpenRoboticsSoftware,
    * or as ":IHMCOpenRoboticsSoftware:ProjectName" if the build is kicked off by a developer workspace. Example usage of this method
    * would be:
    * </p>
    * <pre>
    * {@code
    * dependencies{
    *   compile ihmc.getProjectDependency(":ProjectName")
    *}
    * }
    * </pre>
    *
    * <p>
    * The lookup is very simple in that it starts at the {@link Project#getRootProject()} property and gets a flattened list of all
    * child projects and then returns the first successful result of {@link String#endsWith(java.lang.String)} by comparing with {@link Project#getPath()}.
    * If there are no successful matches, it will return {@code null}.
    * </p>
    *
    * @param projectName The project name to locate
    * @return The {@link Project} matching the name based on the rules described above, or {@code null} if there is no match.
    */
   def Project getProjectDependency(String projectName)
   {
      def projects = getAllProjects(containingProject.getRootProject())

      for (Project project : projects)
      {
         if (project.path.endsWith(projectName))
         {
            containingProject.logger.debug("IHMC Build Extension Found ${project.path} for getProjectDependency(${projectName})")
            return project
         }
      }

      throw new GradleException("Could not find project with name ${projectName}.\nPlease make sure that there is a directory with the given name and that it " +
            "contains a 'build.gradle' file somewhere in your workspace.")
   }

   @Deprecated
   /**
    * <p>
    * Uses the same search mechanism as {@link IHMCBuildExtension#getProjectDependency(java.lang.String)} but returns the
    * fully qualified Gradle path to the project as a String instead of the project object.
    * </p>
    *
    * @param projectName The project name to locate
    * @return The fully qualified Gradle path to the project as given by {@link Project#getPath()}
    */
   def String getProjectDependencyGradlePath(String projectName)
   {
      return getProjectDependency(projectName).path
   }

   @Deprecated
   def DefaultProjectDependency getProjectTestDependency(String projectName)
   {
      String depPath = getProjectDependencyGradlePath(projectName)

      return containingProject.dependencies.project(path: "${depPath}", configuration: 'testOutput')
   }

   @Deprecated
   def void configureForIHMCOpenSourceBintrayPublish(boolean isDryRun, String mavenPublicationName, String bintrayRepoName, List<String> packageLabels)
   {
      containingProject.configure(containingProject) { projectToConfigure ->
         apply plugin: 'com.jfrog.bintray'

         bintray {
            user = projectToConfigure.hasProperty("bintray_user") ? projectToConfigure.bintray_user : "invalid"
            key = projectToConfigure.hasProperty("bintray_key") ? projectToConfigure.bintray_key : "invalid"

            if (user.equals("invalid"))
            {
               projectToConfigure.logger.debug("Bintray user name property not set. Please set the 'bintray_user' property in ~/.gradle/gradle.properties. See https://github.com/bintray/gradle-bintray-plugin")
            }

            if (key.equals("invalid"))
            {
               projectToConfigure.logger.debug("Bintray API key property not set. Please set the 'bintray_key' property in ~/.gradle/gradle.properties. See https://github.com/bintray/gradle-bintray-plugin")
            }

            dryRun = isDryRun
            publish = false

            publications = [mavenPublicationName]

            pkg {
               repo = bintrayRepoName
               name = projectToConfigure.name
               userOrg = 'ihmcrobotics'
               desc = "IHMC Open Robotics Software Project ${projectToConfigure.name}"

               websiteUrl = projectToConfigure.ext.vcsUrl
               issueTrackerUrl = "${projectToConfigure.ext.vcsUrl}/issues"
               vcsUrl = "${projectToConfigure.ext.vcsUrl}.git"

               licenses = [projectToConfigure.ext.bintrayLicenseName]
               labels = packageLabels
               publicDownloadNumbers = true

               version {
                  name = projectToConfigure.version
                  desc = "IHMC Open Robotics Software Project ${projectToConfigure.name} v${projectToConfigure.version}"
                  released = new Date()
                  vcsTag = "v${projectToConfigure.version}"
               }
            }
         }
      }
   }

   @Deprecated
   def void devCheck()
   {
      println containingProject.name
   }

   @Deprecated
   private List<Project> getAllProjects(Project rootProject)
   {
      def ret = new ArrayList<Project>()
      ret.add(rootProject)

      if (!rootProject.childProjects.isEmpty())
      {
         getAllProjectsFlattened(rootProject.childProjects.values(), ret)
      }

      return ret
   }

   @Deprecated
   private void getAllProjectsFlattened(Collection<Project> projects, List<Project> flatProjects)
   {
      for (Project project : projects)
      {
         if (!project.childProjects.isEmpty())
         {
            getAllProjectsFlattened(project.childProjects.values(), flatProjects)
         } else
         {
            flatProjects.add(project)
         }
      }
   }

   @Deprecated
   /**
    * @use {@link setupCommonArtifactProxies}
    */
   def Closure ihmcDefaultArtifactProxies()
   {
      setupCommonArtifactProxies()
   }
}
