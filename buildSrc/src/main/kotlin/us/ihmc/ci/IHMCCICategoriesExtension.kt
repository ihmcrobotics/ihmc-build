package us.ihmc.ci

import org.gradle.api.Project

open class IHMCCICategoriesExtension(private val project: Project)
{
   val categories = hashMapOf<String, IHMCCICategory>()

   fun create(name: String, configuration: IHMCCICategory.() -> Unit)
   {
      val category = IHMCCICategory(name)
      configuration.invoke(category)
      categories.put(name, category)
   }

   fun create(name: String): IHMCCICategory
   {
      val category = IHMCCICategory(name)
      categories.put(name, category)
      return category
   }

   fun get(name: String): IHMCCICategory
   {
      return categories.get(name)!!
   }
}