package io.jenkins.plugins.gitlabbranchsource.Cause;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.gitlab4j.api.webhook.EventUser;
import org.gitlab4j.api.webhook.NoteEvent;
import org.junit.jupiter.api.Test;

class GitLabMergeRequestNoteDataTest {

    private static NoteEvent note(String body, String username) {
        NoteEvent event = new NoteEvent();
        NoteEvent.ObjectAttributes attributes = new NoteEvent.ObjectAttributes();
        attributes.setNote(body);
        event.setObjectAttributes(attributes);
        if (username != null) {
            EventUser user = new EventUser();
            user.setUsername(username);
            event.setUser(user);
        }
        return event;
    }

    @Test
    void exposesCommentAndAuthor() {
        Map<String, String> vars = new GitLabMergeRequestNoteData(note("test in dev", "jane")).getBuildVariables();
        assertEquals("test in dev", vars.get("GITLAB_COMMENT_TRIGGER"));
        assertEquals("jane", vars.get("GITLAB_COMMENT_AUTHOR"));
        assertEquals("note", vars.get("GITLAB_OBJECT_KIND"));
    }

    @Test
    void missingUserGivesEmptyAuthor() {
        Map<String, String> vars = new GitLabMergeRequestNoteData(note("rebuild", null)).getBuildVariables();
        assertEquals("", vars.get("GITLAB_COMMENT_AUTHOR"));
    }
}
