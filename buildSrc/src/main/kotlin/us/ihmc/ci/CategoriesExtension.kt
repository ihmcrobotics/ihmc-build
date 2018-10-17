package us.ihmc.ci

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

class CategoriesExtension(private val project: Project)
{
   val categories = hashMapOf<String, Category>()

   fun create(name: String, configuration: Category.() -> Unit)
   {
      val category = Category(name, project)
      configuration.invoke(category)
      categories.put(name, category)
   }
}