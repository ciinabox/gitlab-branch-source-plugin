package io.jenkins.plugins.gitlabbranchsource;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Builds a merge request's job when someone approves or unapproves it, even though its revision
 * didn't change. See {@link GitLabMergeRequestApprovalTrigger}.
 */
public class TriggerMRApprovalTrait extends SCMSourceTrait {

    /** GitLab's merge request approval actions. */
    public static final Set<String> ALL_ACTIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("approval", "approved", "unapproval", "unapproved")));

    /** Comma-separated actions to build on; empty means all of {@link #ALL_ACTIONS}. */
    private final String actions;

    /**
     * Constructor.
     *
     * @param actions comma-separated approval actions to build on (approval, approved, unapproval,
     *     unapproved); empty for all of them
     */
    @DataBoundConstructor
    public TriggerMRApprovalTrait(String actions) {
        this.actions = actions;
    }

    public String getActions() {
        return actions;
    }

    /**
     * The configured actions, lowercased; unknown ones are dropped.
     *
     * @return the actions to build on
     */
    @NonNull
    public Set<String> getActionSet() {
        if (actions == null || actions.trim().isEmpty()) {
            return ALL_ACTIONS;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String a : actions.split(",")) {
            String action = a.trim().toLowerCase(Locale.ROOT);
            if (ALL_ACTIONS.contains(action)) {
                set.add(action);
            }
        }
        return set;
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        ((GitLabSCMSourceContext) context).withMRApprovalTrigger(true, getActionSet());
    }

    @Extension
    @Symbol("mrTriggerApproval")
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TriggerMRApprovalTrait_displayName();
        }

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitLabSCMSourceContext.class;
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitLabSCMSource.class;
        }
    }
}
