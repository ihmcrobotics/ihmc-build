package us.ihmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by dstephen on 1/26/16.
 */
class IHMCBuild implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.task('poopyPants') << {
            println "Poopy Pants from ${project.name}"
        }
    }
}
