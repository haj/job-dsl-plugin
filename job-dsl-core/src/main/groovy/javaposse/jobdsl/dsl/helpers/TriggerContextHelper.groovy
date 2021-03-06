package javaposse.jobdsl.dsl.helpers

import com.google.common.base.Preconditions
import javaposse.jobdsl.dsl.JobType
import javaposse.jobdsl.dsl.WithXmlAction

/**
 triggers {
 scm(String cronString)
 cron(String cronString)
 }
 */
class TriggerContextHelper extends AbstractContextHelper<TriggerContext> {

    TriggerContextHelper(List<WithXmlAction> withXmlActions, JobType jobType) {
        super(withXmlActions, jobType)
    }

    /**
     * Public method available on job {}
     * @param closure
     * @return
     */
    def triggers(Closure closure) {
        execute(closure, new TriggerContext(withXmlActions, type, []))
    }

    Closure generateWithXmlClosure(TriggerContext context) {
        return { Node project ->
            def triggersNode
            if (project.triggers.isEmpty()) {
                triggersNode = project.appendNode('triggers', [class:'vector'])
            } else {
                triggersNode = project.triggers[0]
            }
            context.triggerNodes.each {
                triggersNode << it
            }
        }
    }
}

class GerritEventContext implements Context {
    def eventShortNames = []

    def propertyMissing(String shortName) {
        eventShortNames << shortName
    }
}

class GerritSpec {
    GerritSpec(String raw) {
        def idx = raw.indexOf(':')
        def prefix = (idx == -1)?'':raw.substring(0, idx).toUpperCase()
        if( availableTypes.contains(prefix)) {
            type = prefix
            pattern = raw.substring(idx+1)
        } else {
            type = 'PLAIN'
            pattern = raw
        }
    }
    def availableTypes = ['ANT', 'PLAIN', 'REG_EXP']
    String type
    String pattern
}

class GerritContext implements Context {
    GerritEventContext eventContext = new GerritEventContext()
    Closure configureClosure
    def projects = []

    def configure(Closure configureClosure) {
        // save for later
        this.configureClosure = configureClosure
    }

    def events(Closure eventClosure) {
        AbstractContextHelper.executeInContext(eventClosure, eventContext)
    }

    def project(String projectName, List<String> branches) {
        projects << [new GerritSpec(projectName), branches.collect { new GerritSpec(it) }]
    }

    def project(String projectName, String branch) {
        project(projectName, [branch])
    }
}

class TriggerContext implements Context {
    List<WithXmlAction> withXmlActions
    JobType jobType
    List<Node> triggerNodes

    TriggerContext(List<WithXmlAction> withXmlActions = [], JobType jobType = JobType.Freeform, List<Node> triggerNodes = []) {
        this.withXmlActions = withXmlActions
        this.jobType = jobType
        this.triggerNodes = triggerNodes
    }

    def cron(String cronString) {
        Preconditions.checkNotNull(cronString)
        triggerNodes << new NodeBuilder().'hudson.triggers.TimerTrigger' {
            spec cronString
        }
    }

    /**
     <triggers class="vector">
     <hudson.triggers.SCMTrigger>
     <spec>10 * * * *</spec>
     </hudson.triggers.SCMTrigger>
     </triggers>
     */
    def scm(String cronString) {
        Preconditions.checkNotNull(cronString)
        triggerNodes << new NodeBuilder().'hudson.triggers.SCMTrigger' {
            spec cronString
        }
    }

    /**
     <com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger>
     <spec></spec>
     <gerritProjects>
     <com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject>
     <compareType>PLAIN</compareType>
     <pattern>test-project</pattern>
     <branches>
     <com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch>
     <compareType>ANT</compareType>
     <pattern>**</pattern>
     </com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch>
     </branches>
     </com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject>
     </gerritProjects>
     <silentMode>false</silentMode>
     <escapeQuotes>true</escapeQuotes>
     <buildStartMessage></buildStartMessage>
     <buildFailureMessage></buildFailureMessage>
     <buildSuccessfulMessage></buildSuccessfulMessage>
     <buildUnstableMessage></buildUnstableMessage>
     <buildNotBuiltMessage></buildNotBuiltMessage>
     <buildUnsuccessfulFilepath></buildUnsuccessfulFilepath>
     <customUrl></customUrl>
     <triggerOnEvents>
     <com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeMergedEvent/>
     <com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent/>
     </triggerOnEvents>
     <dynamicTriggerConfiguration>false</dynamicTriggerConfiguration>
     <triggerConfigURL></triggerConfigURL>
     <triggerInformationAction/>
     </com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger>
     */
    /**
     *
     * @param triggerEvents Can be ommited and the plugin will user PatchsetCreated and DraftPublished by default. Provide in
     *                      show name format: ChangeMerged, CommentAdded, DraftPublished, PatchsetCreated, RefUpdated
     * @return
     */
    def gerrit(Closure contextClosure = null) {
        // See what they set up in the contextClosure before generating xml
        GerritContext gerritContext = new GerritContext()
        AbstractContextHelper.executeInContext(contextClosure, gerritContext)

        def nodeBuilder = new NodeBuilder()
        def gerritNode = nodeBuilder.'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger' {
            spec ''
            if(gerritContext.projects) {
                gerritProjects {
                    gerritContext.projects.each { GerritSpec project, List<GerritSpec> brancheSpecs ->
                        'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
                            compareType project.type
                            pattern project.pattern
                            branches {
                                brancheSpecs.each { GerritSpec branch ->
                                    'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
                                        compareType branch.type
                                        pattern branch.pattern
                                    }
                                }
                            }
                        }
                    }
                }
            }
            silentMode false
            escapeQuotes true
            buildStartMessage ''
            buildFailureMessage ''
            buildSuccessfulMessage ''
            buildUnstableMessage ''
            buildNotBuiltMessage ''
            buildUnsuccessfulFilepath ''
            customUrl ''
            if(gerritContext.eventContext.eventShortNames) {
                triggerOnEvents {
                    gerritContext.eventContext.eventShortNames.each { eventShortName ->
                        "com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.Plugin${eventShortName}Event" ''
                    }
                }
            }
            gerritBuildStartedVerifiedValue 0
            gerritBuildStartedCodeReviewValue 0
            gerritBuildSuccessfulVerifiedValue 1
            gerritBuildSuccessfulCodeReviewValue 2
            gerritBuildFailedVerifiedValue '-2'
            gerritBuildFailedCodeReviewValue '-2'
            gerritBuildUnstableVerifiedValue '-1'
            gerritBuildUnstableCodeReviewValue '-1'
            gerritBuildNotBuiltVerifiedValue 0
            gerritBuildNotBuiltCodeReviewValue 0
            dynamicTriggerConfiguration false
            triggerConfigURL ''
            triggerInformationAction ''
        }

        // Apply their overrides
        if (gerritContext.configureClosure) {
            WithXmlAction action = new WithXmlAction(gerritContext.configureClosure)
            action.execute(gerritNode)
        }

        triggerNodes << gerritNode

    }

    /**
     * If set to <code>true</code>, Jenkins will parse the POMs of this project, and see if any of its snapshot
     * dependencies are built on this Jenkins as well. If so, Jenkins will set up build dependency relationship so that
     * whenever the dependency job is built and a new SNAPSHOT jar is created, Jenkins will schedule a build of this
     * project. Defaults to <code>false</code>.
     * @param checkSnapshotDependencies set to <code>true</code> to check snapshot dependencies
     */
    def snapshotDependencies(boolean checkSnapshotDependencies) {
        Preconditions.checkState jobType == JobType.Maven, "snapshotDependencies can only be applied for Maven jobs"
        withXmlActions << new WithXmlAction({
            it.children().removeAll { it instanceof Node && it.name() == "ignoreUpstremChanges" }
            it.appendNode "ignoreUpstremChanges", !checkSnapshotDependencies
        })
    }
}
