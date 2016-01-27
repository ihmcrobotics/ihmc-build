package us.ihmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by dstephen on 1/26/16.
 */
class IHMCBuild implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.configure(project) {
            apply plugin: 'java'
            apply plugin: 'groovy'
        }

        addExtensions(project)

        setupSourceSetStructure(project)

        addTasks(project)
    }

    private void addExtensions(Project project) {
        project.extensions.create("ihmc", IHMCBuildExtension, project)
    }

    private void addTasks(Project project) {
        project.task('checkIHMCBuild') << {
            println "IHMC Build being applied from ${project.name}"
        }
    }

    private void setupSourceSetStructure(Project project) {
        project.sourceSets {
            main {
                java {
                    srcDirs = ['src']
                }

                groovy {
                    srcDirs = ['groovySrc']
                }

                resources {
                    srcDirs = ['src', 'resources']
                }
            }

            test {
                java {
                    srcDirs = ['test']
                }

                groovy {
                    srcDirs = ['groovyTest']
                }
            }
        }
    }
}
