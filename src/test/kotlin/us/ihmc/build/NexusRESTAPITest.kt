package us.ihmc.build

import kong.unirest.Unirest
import kong.unirest.json.JSONObject
import org.gradle.api.GradleException
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

fun main()
{
    val nexusUsername = "username"
    val nexusPassword = "password"
    var continuationToken = "first_page"
    val requestUrl = "https://nexus.ihmc.us/service/rest/v1/search?repository=proprietary-releases&maven.groupId=us.ihmc"
    val matches = arrayListOf<JSONObject>()
    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    while (continuationToken != "no_more_pages")
    {
        Unirest.get(if (continuationToken == "first_page") requestUrl else "$requestUrl&continuationToken=$continuationToken")
                .basicAuth(nexusUsername, nexusPassword)
                .asJson()
                .ifSuccess { response ->
                    val bodyObject: JSONObject = response.body.`object`
                    if (bodyObject.isNull("continuationToken"))
                        continuationToken = "no_more_pages"
                    else
                        continuationToken = bodyObject.getString("continuationToken")
                    val items = bodyObject.getJSONArray("items")
                    for (item in items)
                    {
                        val assets = (item as JSONObject).getJSONArray("assets")
                        for (asset in assets)
                        {
                            val assetObject = asset as JSONObject
                            val path = assetObject.getString("path")
                            if (path.matches(Regex(".*")))
                            {
                                matches.add(assetObject)
                                if (path == "us/ihmc/impulse-series-4-controller/0.1.1/impulse-series-4-controller-0.1.1.pom")
                                {
                                    var bytes: ByteArray? = null
                                    val downloadUrl = asset.getString("downloadUrl")
                                    Unirest.get(downloadUrl)
                                            .basicAuth(nexusUsername, nexusPassword)
                                            .asBytes()
                                            .ifSuccess { response ->
                                                bytes = response.body
                                                val inputStream = ByteArrayInputStream(bytes)
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

                                                        println(dependencyGroupId)
                                                        println(dependencyArtifactId)
                                                        println(dependencyVersion)
                                                    }
                                                }
                                                catch (e: Exception)
                                                {
                                                    e.printStackTrace()
                                                }
                                            }
                                            .ifFailure { response ->
                                                throw GradleException("Problem authenticating or retrieving item from Nexus: $downloadUrl. " +
                                                        "Try logging into https://nexus.ihmc.us with the credentials used " +
                                                        "(nexusUsername and nexusPassword properties) and see if the item is there.")
                                            }
                                }
                            }
                        }
                    }
                }
                .ifFailure { response ->
                    throw GradleException("Problem authenticating or retrieving item from Nexus: $requestUrl. " +
                            "Try logging into https://nexus.ihmc.us with the credentials used " +
                            "(nexusUsername and nexusPassword properties) and see if the item is there.")
                }
    }
    for (match in matches)
    {
        println(match)
    }
}
