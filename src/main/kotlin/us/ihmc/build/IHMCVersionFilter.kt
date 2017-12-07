package us.ihmc.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.initialization.IncludedBuild
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class IHMCVersionFilter(val ihmcBuildExtension: IHMCBuildExtension, val project: Project)
{
   private val logger = project.logger
   private val includedBuildMap: HashMap<String, Boolean> = hashMapOf()
   private val artifactory: Artifactory by lazy {
      val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
      builder.url = "https://artifactory.ihmc.us/artifactory"
      if (!ihmcBuildExtension.openSource)
      {
         builder.username = ihmcBuildExtension.artifactoryUsername
         builder.password = ihmcBuildExtension.artifactoryPassword
      }
      builder.build()
   }
   private val repositoryVersions: HashMap<String, TreeSet<String>> = hashMapOf()
   private val pomDependencies: HashMap<String, ArrayList<ArrayList<String>>> = hashMapOf()
   private val documentBuilderFactory by lazy {
      DocumentBuilderFactory.newInstance()
   }
   
   internal fun getExternalDependencyVersion(groupId: String, artifactId: String, declaredVersion: String): String
   {
      var externalDependencyVersion: String
      
      // Make sure POM is correct
      if (artifactIsIncludedBuild(artifactId))
      {
         externalDependencyVersion = ihmcBuildExtension.publishVersion
      }
      else
      {
         if (declaredVersion.startsWith("SNAPSHOT"))
         {
            var sanitizedDeclaredVersion = declaredVersion.replace("-BAMBOO", "")
            
            // Use Bamboo variables to resolve the version
            if (ihmcBuildExtension.isBambooBuild)
            {
               var closestVersion = "NOT-FOUND"
               if (ihmcBuildExtension.isChildBuild) // Match to parent build, exact branch and version
               {
                  var childVersion = "SNAPSHOT"
                  if (ihmcBuildExtension.isBranchBuild)
                  {
                     childVersion += "-${ihmcBuildExtension.branchName}"
                  }
                  childVersion += "-${ihmcBuildExtension.buildNumber}"
                  closestVersion = matchVersionFromRepositories(groupId, artifactId, childVersion)
               }
               if (closestVersion.contains("NOT-FOUND") && ihmcBuildExtension.isBranchBuild) // Try latest from branch
               {
                  closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT-${ihmcBuildExtension.branchName}")
               }
               if (closestVersion.contains("NOT-FOUND")) // Try latest without branch
               {
                  closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT")
               }
               externalDependencyVersion = closestVersion
            }
            else
            {
               // For users
               if (sanitizedDeclaredVersion.endsWith("-LATEST")) // Finds latest version
               {
                  externalDependencyVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, declaredVersion.substringBefore("-LATEST"))
               }
               else // Get exact match on end of string
               {
                  externalDependencyVersion = matchVersionFromRepositories(groupId, artifactId, declaredVersion)
               }
            }
         }
         else // Pass directly to gradle as declared
         {
            externalDependencyVersion = declaredVersion
         }
      }
      
      logInfo(logger, "Passing version to Gradle: $groupId:$artifactId:$externalDependencyVersion")
      return externalDependencyVersion
   }
   
   private fun artifactIsIncludedBuild(artifactId: String): Boolean
   {
      if (!includedBuildMap.containsKey(artifactId))
      {
         for (includedBuild in getIncludedBuilds())
         {
            if (artifactId == includedBuild.name)
            {
               includedBuildMap[artifactId] = true
               return true
            }
            else if (artifactId.startsWith(includedBuild.name))
            {
               for (extraSourceSet in IHMCBuildProperties(project.logger).load(includedBuild.projectDir.toPath()).extraSourceSets)
               {
                  if (artifactId == (includedBuild.name + "-$extraSourceSet"))
                  {
                     includedBuildMap[artifactId] = true
                     return true
                  }
               }
            }
         }
         
         includedBuildMap[artifactId] = false
         return false
      }
      
      return includedBuildMap[artifactId]!!
   }
   
   fun getIncludedBuilds(): Collection<IncludedBuild>
   {
      if (ihmcBuildExtension.isBuildRoot)
      {
         return project.gradle.includedBuilds
      }
      else
      {
         return project.gradle.parent!!.includedBuilds
      }
   }
   
   private fun getSnapshotRepositoryList(): List<String>
   {
      if (ihmcBuildExtension.openSource)
      {
         return listOf("snapshots")
      }
      else
      {
         return listOf("snapshots", "proprietary-snapshots")
      }
   }
   
   private fun searchRepositories(groupId: String, artifactId: String): Set<String>
   {
      if (!repositoryVersions.containsKey("$groupId:$artifactId"))
      {
         repositoryVersions["$groupId:$artifactId"] = sortedSetOf<String>()
         
         if (ihmcBuildExtension.offline)
         {
            val gradleCache = Paths.get(System.getProperty("user.home")).resolve(".gradle/caches/modules-2/files-2.1")
            val artifactPath = gradleCache.resolve(groupId).resolve(artifactId)
            
            for (entry in artifactPath.toFile().list())
            {
               repositoryVersions["$groupId:$artifactId"]!!.add(entry)
            }
         }
         else
         {
            for (repository in getSnapshotRepositoryList())
            {
               for (repoPath in searchArtifactory(repository, groupId, artifactId))
               {
                  if (repoPath.itemPath.matches(Regex(".*\\d\\.jar$")))
                  {
                     repositoryVersions["$groupId:$artifactId"]!!.add(itemPathToVersion(repoPath.itemPath, artifactId))
                  }
               }
            }
         }
      }
      
      return repositoryVersions["$groupId:$artifactId"]!!
   }
   
   private fun anyVersionExists(groupId: String, artifactId: String): Boolean
   {
      return !searchRepositories(groupId, artifactId).isEmpty()
   }
   
   private fun versionExists(groupId: String, artifactId: String, version: String): Boolean
   {
      if (repositoryVersions.containsKey("$groupId:$artifactId") && repositoryVersions["$groupId:$artifactId"]!!.contains(version))
      {
         return true
      }
      
      if (!ihmcBuildExtension.offline)
      {
         for (repository in getSnapshotRepositoryList())
         {
            if (searchArtifactory(repository, groupId, artifactId, version).size > 0)
            {
               if (repositoryVersions.containsKey("$groupId:$artifactId"))
               {
                  repositoryVersions["$groupId:$artifactId"]!!.add(version)
               }
               logInfo(logger, "Found version circumventing Artifactory bug: $groupId:$artifactId:$version")
               return true
            }
         }
      }
      
      return false
   }
   
   private fun loadPOMDependencies(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (ihmcBuildExtension.offline)
      {
         return loadPOMDependenciesMavenLocal(groupId, artifactId, versionToCheck)
      }
      else
      {
         return loadPOMDependenciesArtifactory(groupId, artifactId, versionToCheck)
      }
   }
   
   private fun loadPOMDependenciesArtifactory(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf()
         
         var pomPath: RepoPath
         for (repository in getSnapshotRepositoryList())
         {
            for (repoPath in searchArtifactory(repository, groupId, artifactId, versionToCheck))
            {
               if (repoPath.itemPath.matches(Regex(".*\\d\\.pom$")))
               {
                  logInfo(logger, "Hitting Artifactory for POM: " + repoPath.itemPath)
                  val inputStream = downloadItemFromArtifactory(repository, repoPath)
                  
                  parsePOMInputStream(inputStream, groupId, artifactId, versionToCheck)
               }
            }
         }
      }
      
      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }
   
   private fun searchArtifactory(repository: String, groupId: String, artifactId: String): List<RepoPath>
   {
      try
      {
         return artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).doSearch()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$groupId/$artifactId/${ihmcBuildExtension.version}")
      }
   }
   
   private fun searchArtifactory(repository: String, groupId: String, artifactId: String, version: String): List<RepoPath>
   {
      try
      {
         return artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).version(version).doSearch()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$groupId/$artifactId/$version")
      }
   }
   
   private fun downloadItemFromArtifactory(repository: String, repoPath: RepoPath): InputStream
   {
      try
      {
         return artifactory.repository(repository).download(repoPath.itemPath).doDownload()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$repoPath")
      }
   }
   
   private fun artifactoryException(path: String): GradleException
   {
      return GradleException("Problem authenticating or retrieving item from Artifactory: $path. Try logging into artifactory.ihmc.us with the credentials used (artifactoryUsername and artifactoryPassword properties) and see if the item is there.")
   }
   
   private fun parsePOMInputStream(inputStream: InputStream?, groupId: String, artifactId: String, versionToCheck: String)
   {
      try
      {
         val documentBuilder = documentBuilderFactory.newDocumentBuilder()
         val document = documentBuilder.parse(inputStream);
         
         val dependencyTags = document.getElementsByTagName("dependency")
         for (i in 0 until dependencyTags.length)
         {
            val dependencyGroupId = dependencyTags.item(i).childNodes.item(1).textContent
            val dependencyArtifactId = dependencyTags.item(i).childNodes.item(3).textContent
            val dependencyVersion = dependencyTags.item(i).childNodes.item(5).textContent
            
            if (dependencyVersion.contains("SNAPSHOT") && anyVersionExists(dependencyGroupId, dependencyArtifactId))
            {
               val arrayDependency: ArrayList<String> = arrayListOf()
               arrayDependency.add(dependencyGroupId)
               arrayDependency.add(dependencyArtifactId)
               arrayDependency.add(dependencyVersion)
               
               pomDependencies["$groupId:$artifactId:$versionToCheck"]!!.add(arrayDependency)
            }
         }
      }
      catch (e: Exception)
      {
         e.printStackTrace()
      }
   }
   
   private fun loadPOMDependenciesMavenLocal(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf()
         
         logInfo(logger, "Hitting Maven Local for POM: user.home/.gradle/caches/modules-2/files-2.1/$groupId/$artifactId/$versionToCheck")
         val gradleCache = Paths.get(System.getProperty("user.home")).resolve(".gradle/caches/modules-2/files-2.1")
         val versionPath = gradleCache.resolve(groupId).resolve(artifactId).resolve(versionToCheck)
         
         var pomFile: File? = null
         for (hashEntry in versionPath.toFile().list())
         {
            for (fileEntry in versionPath.resolve(hashEntry).toFile().list())
            {
               if (fileEntry.endsWith(".pom"))
               {
                  pomFile = versionPath.resolve(hashEntry).resolve(fileEntry).toFile()
               }
            }
         }
         
         parsePOMInputStream(FileInputStream(pomFile), groupId, artifactId, versionToCheck)
      }
      
      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }
   
   private fun performPOMCheck(groupId: String, artifactId: String, versionToCheck: String): Boolean
   {
      if (!versionExists(groupId, artifactId, versionToCheck))
      {
         logInfo(logger, "Version doesn't exist: $groupId:$artifactId:$versionToCheck")
         return false
      }
      else
      {
         for (dependency in loadPOMDependencies(groupId, artifactId, versionToCheck))
         {
            if (!performPOMCheck(dependency[0], dependency[1], dependency[2]))
            {
               return false
            }
         }
         
         return true
      }
   }
   
   private fun itemPathToVersion(itemPath: String, artifactId: String): String
   {
      val split: List<String> = itemPath.split("/")
      val artifact: String = split[split.size - 1]
      val withoutDotJar: String = artifact.split(".jar")[0]
      val version: String = withoutDotJar.substring(artifactId.length + 1)
      
      return version
   }
   
   private fun matchVersionFromRepositories(groupId: String, artifactId: String, versionMatcher: String): String
   {
      for (repositoryVersion in searchRepositories(groupId, artifactId))
      {
         if (repositoryVersion.endsWith(versionMatcher))
         {
            return repositoryVersion
         }
      }
      
      return "MATCH-NOT-FOUND-$versionMatcher"
   }
   
   private fun latestPOMCheckedVersionFromRepositories(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
      
      if (highestVersion.contains("NOT-FOUND"))
         return highestVersion
      
      while (!performPOMCheck(groupId, artifactId, highestVersion))
      {
         logInfo(logger, "Failed POM check: $groupId:$artifactId:$highestVersion")
         repositoryVersions["$groupId:$artifactId"]!!.remove(highestVersion)
         highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
         logInfo(logger, "Rolling back to: $groupId:$artifactId:$highestVersion")
      }
      
      return highestVersion
   }
   
   private fun highestBuildNumberVersion(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var matchedVersion = "LATEST-NOT-FOUND-$versionMatcher"
      var highestBuildNumber: Int = -1
      
      for (repositoryVersion in searchRepositories(groupId, artifactId))
      {
         if (repositoryVersion.matches(Regex("$versionMatcher-\\d+")))
         {
            val buildNumberFromArtifactory: Int = Integer.parseInt(repositoryVersion.split("-").last())
            if (buildNumberFromArtifactory > highestBuildNumber)
            {
               matchedVersion = repositoryVersion
               highestBuildNumber = buildNumberFromArtifactory
            }
         }
      }
      
      return matchedVersion
   }
}