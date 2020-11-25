package us.ihmc.ci

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result;
import org.gradle.api.Project
import org.gradle.api.Task
import org.json.JSONObject

fun Task.configureCIServerSyncTask(testsToTagsMap: Lazy<HashMap<String, HashSet<String>>>,
                                   testProjects: Lazy<List<Project>>,
                                   ciBackendHost: String)
{
   // add JavaCompile for all test projects
   testProjects.value.forEach { testProject ->
      dependsOn(testProject.tasks.getByPath("compileJava"))
   }

   doFirst {
      if (project.properties["ciPlanKey"] == null)
         LogTools.crash("ciServerSync: Please set ciPlanKey = PROJKEY-PLANKEY")

      var ciPlanKey = (project.properties["ciPlanKey"]!! as String).trim()
      LogTools.info("ciPlanKey = $ciPlanKey")
      val discoveredTags = hashSetOf<String>()
      testsToTagsMap.value.forEach { test ->
         if (test.value.isEmpty())
         {
            discoveredTags.add("fast")
         }
         test.value.forEach { tagName ->
            discoveredTags.add(tagName)
         }
      }
      LogTools.info("Discovered tags: $discoveredTags")

      val json = JSONObject()
      json.put("projectName", project.name)
      json.put("ciPlanKey", ciPlanKey)
      json.put("testsToTags", testsToTagsMap.value)

      val url = "http://$ciBackendHost/sync"
      var fail = false
      var message = ""
      val (request, response, result) = Fuel.post(url, listOf(Pair("text", json.toString(2))))
            .timeout(30000)
            .responseString()
      when (result)
      {
         is Result.Failure ->
         {
            val error = result.getException()
            message = "Post request failed: $url\n$error"
            fail = true
         }
         is Result.Success ->
         {
            val jsonObject = JSONObject(result.get())
            message = jsonObject["message"] as String
            fail = jsonObject["fail"] as Boolean
         }
      }

      if (fail) // do this after to avoid exceptions getting caught by Fuel
      {
         LogTools.crash("ciServerSync: $message")
      }
      else
      {
         LogTools.info("ciServerSync: $message")
      }
   }
}