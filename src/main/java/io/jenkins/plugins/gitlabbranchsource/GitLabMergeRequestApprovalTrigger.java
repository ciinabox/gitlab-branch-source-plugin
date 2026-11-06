package io.jenkins.plugins.gitlabbranchsource;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.branch.MultiBranchProject;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.gitlab4j.api.webhook.MergeRequestEvent;

/**
 * Builds a merge request's job when someone approves or unapproves it.
 *
 * <p>Approval events reach multibranch projects as SCM head events too, but those only build when
 * the merge request's revision changed, which an approval never does. This trigger schedules the
 * merge request's job directly, as {@link GitLabMergeRequestCommentTrigger} does for comments, for
 * sources with {@link TriggerMRApprovalTrait}. The build carries the event's variables
 * ({@code GITLAB_OA_ACTION}, {@code GITLAB_USER_USERNAME}, ...) through {@link GitLabWebHookCause}.
 */
public class GitLabMergeRequestApprovalTrigger extends AbstractGitLabJobTrigger<MergeRequestEvent> {

    public static final Logger LOGGER = Logger.getLogger(GitLabMergeRequestApprovalTrigger.class.getName());

    public GitLabMergeRequestApprovalTrigger(MergeRequestEvent payload) {
        super(payload);
    }

    /**
     * Whether {@code action} is one of GitLab's approval actions.
     *
     * @param action the merge request event's {@code object_attributes.action}
     * @return true for approval, approved, unapproval and unapproved
     */
    public static boolean isApprovalAction(String action) {
        return TriggerMRApprovalTrait.ALL_ACTIONS.contains(action);
    }

    /**
     * Whether a source with these actions configured should build for {@code action}.
     *
     * @param action the event's action
     * @param configured the actions configured on the source
     * @return true if the action is an approval action and configured
     */
    static boolean shouldBuild(String action, Set<String> configured) {
        return isApprovalAction(action) && configured.contains(action);
    }

    @Override
    public void isMatch() {
        MergeRequestEvent event = getPayload();
        String action = event.getObjectAttributes().getAction();
        if (!isApprovalAction(action)) {
            return;
        }
        Long iid = event.getObjectAttributes().getIid();
        Long targetProjectId = event.getObjectAttributes().getTargetProjectId();
        Pattern jobName = Pattern.compile("^MR-" + iid + "\\b.*$", Pattern.CASE_INSENSITIVE);
        boolean scheduled = false;
        try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
            for (SCMSourceOwner owner : SCMSourceOwners.all()) {
                if (owner.getParent() instanceof ComputedFolder) {
                    continue;
                }
                for (SCMSource source : owner.getSCMSources()) {
                    if (!(source instanceof GitLabSCMSource)) {
                        continue;
                    }
                    GitLabSCMSource gitLabSCMSource = (GitLabSCMSource) source;
                    GitLabSCMSourceContext context = new GitLabSCMSourceContext(null, SCMHeadObserver.none())
                            .withTraits(gitLabSCMSource.getTraits());
                    if (!context.mrApprovalTriggerEnabled()
                            || !shouldBuild(action, context.mrApprovalTriggerActions())) {
                        continue;
                    }
                    Long projectId = gitLabSCMSource.getProjectId();
                    if (projectId == null || !projectId.equals(targetProjectId)) {
                        continue;
                    }
                    for (Job<?, ?> job : owner.getAllJobs()) {
                        if (!(job.getParent() instanceof MultiBranchProject)) {
                            continue;
                        }
                        MultiBranchProject<?, ?> parent = (MultiBranchProject<?, ?>) job.getParent();
                        if (parent.getSCMSource(gitLabSCMSource.getId()) == gitLabSCMSource
                                && jobName.matcher(job.getName()).matches()) {
                            ParameterizedJobMixIn.scheduleBuild2(
                                    job, 0, new GitLabSCMCauseAction(new GitLabWebHookCause().fromMergeRequest(event)));
                            scheduled = true;
                            LOGGER.log(Level.INFO, "Triggered build for {0} due to MR {1} on {2}", new Object[] {
                                job.getFullName(), action, event.getProject().getPathWithNamespace()
                            });
                            break;
                        }
                    }
                }
            }
        }
        if (!scheduled) {
            LOGGER.log(Level.FINE, "MR {0} on {1} did not match any job with the approval trigger", new Object[] {
                action, event.getProject().getPathWithNamespace()
            });
        }
    }
}
