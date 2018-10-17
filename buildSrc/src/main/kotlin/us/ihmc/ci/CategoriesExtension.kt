package us.ihmc.ci

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

class CategoriesExtension(private val project: Project)
{
   val categories = hashMapOf<String, Category>()

   fun create(name: String, configuration: Category.() -> Unit)
   {
      val category = Category(name)
      configuration.invoke(category)
      categories.put(name, category)
   }

   fun getAllocationAgentJVMArg(): String
   {
      project.project(project.name + "-test").configurations.getByName("compile").files.forEach {
         if (it.name.contains("java-allocation-instrumenter"))
         {
            val allocationJVMArg = "-javaagent:" + it.getAbsolutePath()
            println("[ihmc-ci] Found allocation JVM arg: " + allocationJVMArg)
            return allocationJVMArg
         }
      }

      throw GradleException("[ihmc-ci] Cannot find `java-allocation-instrumenter` on test classpath. Please add it to your test dependencies!")
   }
}