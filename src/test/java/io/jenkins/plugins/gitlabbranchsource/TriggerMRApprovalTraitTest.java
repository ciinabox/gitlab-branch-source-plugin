package io.jenkins.plugins.gitlabbranchsource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.jupiter.api.Test;

class TriggerMRApprovalTraitTest {

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    // decorateContext is protected in SCMSourceTrait; the overrides here are in this package.
    private static GitLabSCMSourceContext context() {
        return new GitLabSCMSourceContext(null, SCMHeadObserver.none());
    }

    private static GitLabSCMSourceContext decorate(TriggerMRApprovalTrait trait) {
        GitLabSCMSourceContext context = context();
        trait.decorateContext(context);
        return context;
    }

    private static GitLabSCMSourceContext decorate(TriggerMRCommentTrait trait) {
        GitLabSCMSourceContext context = context();
        trait.decorateContext(context);
        return context;
    }

    @Test
    void emptyActionsMeanAll() {
        assertEquals(TriggerMRApprovalTrait.ALL_ACTIONS, new TriggerMRApprovalTrait("").getActionSet());
        assertEquals(TriggerMRApprovalTrait.ALL_ACTIONS, new TriggerMRApprovalTrait(null).getActionSet());
    }

    @Test
    void actionsAreTrimmedLowercasedAndFiltered() {
        assertEquals(
                set("approved", "unapproved"),
                new TriggerMRApprovalTrait(" Approved, unapproved ,merge,").getActionSet());
    }

    @Test
    void decoratesContext() {
        GitLabSCMSourceContext context = decorate(new TriggerMRApprovalTrait("approval,unapproval"));
        assertTrue(context.mrApprovalTriggerEnabled());
        assertEquals(set("approval", "unapproval"), context.mrApprovalTriggerActions());
    }

    @Test
    void contextDefaultsToNoApprovalTrigger() {
        GitLabSCMSourceContext context = context();
        assertFalse(context.mrApprovalTriggerEnabled());
        assertEquals(Collections.emptySet(), context.mrApprovalTriggerActions());
    }

    @Test
    void approvalActions() {
        for (String action : TriggerMRApprovalTrait.ALL_ACTIONS) {
            assertTrue(GitLabMergeRequestApprovalTrigger.isApprovalAction(action), action);
        }
        assertFalse(GitLabMergeRequestApprovalTrigger.isApprovalAction("update"));
        assertFalse(GitLabMergeRequestApprovalTrigger.isApprovalAction("open"));
        assertFalse(GitLabMergeRequestApprovalTrigger.isApprovalAction(null));
    }

    @Test
    void shouldBuildOnlyForConfiguredApprovalActions() {
        Set<String> configured = set("approved");
        assertTrue(GitLabMergeRequestApprovalTrigger.shouldBuild("approved", configured));
        assertFalse(GitLabMergeRequestApprovalTrigger.shouldBuild("approval", configured));
        assertFalse(GitLabMergeRequestApprovalTrigger.shouldBuild("update", set("update")));
    }

    @Test
    void commentTraitIgnoreOwnCommentsIsOptIn() {
        TriggerMRCommentTrait trait = new TriggerMRCommentTrait("(?s).*", true);
        assertFalse(trait.isIgnoreOwnComments());
        assertFalse(decorate(trait).ignoreOwnComments());
        trait.setIgnoreOwnComments(true);
        assertTrue(decorate(trait).ignoreOwnComments());
    }
}
