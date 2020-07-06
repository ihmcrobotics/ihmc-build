package us.ihmc.build

import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.model.Factory
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.model.MutableNode
import guru.nidi.graphviz.toGraphviz
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.kotlin.dsl.get
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.HashMap

/**
 * TODO: Support graphing sources and jars
 */
class IHMCDependencyGraphviz(val project: Project)
{

   val previouslyExplored = sortedSetOf<String>()

   lateinit var graph: MutableGraph
   lateinit var nodeMap: HashMap<String, MutableNode>

   init
   {
      project.tasks.create("graphDependencies")
      {
         doLast {

            graph = Factory.mutGraph("graph name?").setDirected(true)
            nodeMap = HashMap()

            val projectName = "${project.group}:${project.name}:${project.version}"
            nodeMap[projectName] = Factory.mutNode(projectName)

            for (firstLevelModuleDependency in project.configurations["runtimeClasspath"].resolvedConfiguration.firstLevelModuleDependencies)
            {
               val id = "${firstLevelModuleDependency.moduleGroup}:${firstLevelModuleDependency.moduleName}:${firstLevelModuleDependency.moduleVersion}"

               if (!nodeMap.containsKey(id))
               {
                  nodeMap[id] = Factory.mutNode(id)
               }

               nodeMap[projectName]!!.addLink(nodeMap[id])

               if (!previouslyExplored.contains(id))
               {
                  previouslyExplored.add(id)
                  recursiveHandleDependency(firstLevelModuleDependency, id)
               }
            }

            nodeMap.values.forEach { graph.add(it) }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmssSSS");
            val calendar = Calendar.getInstance();
            val timestamp = dateFormat.format(calendar.getTime());
            val filePathName = System.getProperty("user.home") + "/.ihmc/logs/" + timestamp + "_" + "_DependencyGraph.png";
//            val filePathName = System.getProperty("user.home") + "/" + LocalDateTime.now().format(dateFormatter) + "_DependencyGraph.png"

            graph.toGraphviz().render(Format.PNG).toFile(File(filePathName))

            LogTools.quiet("Dependency graph saved to " + filePathName)
         }
      }
   }

   private fun recursiveHandleDependency(firstLevelModuleDependency: ResolvedDependency, id: String)
   {
//      LogTools.warn("nodeMap size: " + nodeMap.)
//      LogTools.warn("nodeMap size: " + graph.tot)


      for (child in firstLevelModuleDependency.children)
      {
         val childId = "${child.moduleGroup}:${child.moduleName}:${child.moduleVersion}"

         if (!nodeMap.containsKey(childId))
         {
            nodeMap[childId] = Factory.mutNode(childId)
         }

         nodeMap[id]!!.addLink(nodeMap[childId])

         if (!previouslyExplored.contains(childId))
         {
            previouslyExplored.add(childId)
            recursiveHandleDependency(firstLevelModuleDependency, childId)
         }
      }
   }
}