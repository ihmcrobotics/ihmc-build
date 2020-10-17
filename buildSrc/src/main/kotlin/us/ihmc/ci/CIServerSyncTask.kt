package us.ihmc.ci

import com.github.kittinunf.fuel.Fuel
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.json.JSONObject
import java.nio.charset.Charset

object CIServerSyncTask
{
   fun configureTask(testsToTagsMap: Lazy<HashMap<String, HashSet<String>>>,
                     testProjects: Lazy<List<Project>>,
                     ciBackendHost: String): (Task) -> Unit
   {
      return { task ->
         // add JavaCompile for all test projects
         testProjects.value.forEach { testProject ->
            task.dependsOn(testProject.tasks.getByPath("compileJava"))
         }

         task.doFirst {
            if (project.properties["ciPlanKey"] == null)
               throw GradleException("[ihmc-ci] ciServerSync: Please set ciPlanKey = PROJKEY-PLANKEY")

            var ciPlanKey = (project.properties["ciPlanKey"]!! as String).trim()
            project.logger.info("[ihmc-ci] ciPlanKey = $ciPlanKey")
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
            project.logger.info("[ihmc-ci] Discovered tags: $discoveredTags")

            val json = JSONObject()
            json.put("projectName", project.name)
            json.put("ciPlanKey", ciPlanKey)
            json.put("testsToTags", testsToTagsMap.value)

            val url = "http://$ciBackendHost/sync"
            var fail = false
            var message = ""
            val request = Fuel.post(url, listOf(Pair("text", json.toString(2)))).timeout(30000)
            val cancellableRequest = request.response { req, res, result ->
               result.fold({ byteArray ->
                              val responseData = res.data.toString(Charset.defaultCharset())
                              val jsonObject = JSONObject(responseData)
                              message = jsonObject["message"] as String
                              fail = jsonObject["fail"] as Boolean
                           },
                           { error ->
                              message = "Post request failed: $url\n$error"
                              fail = true
                           })
            }
            cancellableRequest.join() // the above call is async, so wait for it

            if (fail) // do this after to avoid exceptions getting caught by Fuel
            {
               throw GradleException("[ihmc-ci] ciServerSync: $message")
            }
            else
            {
               LogTools.info("[ihmc-ci] ciServerSync: $message")
            }
         }
      }
   }
}