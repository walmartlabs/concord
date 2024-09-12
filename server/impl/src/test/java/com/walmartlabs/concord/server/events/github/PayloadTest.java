package com.walmartlabs.concord.server.events.github;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerEntryBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadTest {

    private static final String BASE_REPO = "https://repo/base.git";
    private static final String FORK_REPO = "https://repo/fork.git";

    @Test
    void testSamePRRepo() {
        Payload payload = createPRPayload("pull_request", BASE_REPO);

        assertFalse(payload.isPullRequestFromDifferentRepo());
    }

    @Test
    void testDifferentPRRepo() {
        Payload payload = createPRPayload("pull_request", FORK_REPO);

        assertTrue(payload.isPullRequestFromDifferentRepo());
    }

    @Test
    void testSkipTriggerSamePRRepo() {
        TriggerEntry t = generateTrigger(true);

        boolean skip = GithubTriggerV2Processor.skipTrigger(t, "pull_request", createPRPayload("pull_request", BASE_REPO));
        assertFalse(skip);
    }

    @Test
    void testSkipTriggerDifferentPRRepo() {
        TriggerEntry t = generateTrigger(true);

        boolean skip = GithubTriggerV2Processor.skipTrigger(t, "pull_request", createPRPayload("pull_request", FORK_REPO));
        assertTrue(skip);
    }

    @Test
    void testSkipTriggerDifferentPRRepoNoUseEventCommitId() {
        TriggerEntry t = generateTrigger(false);

        boolean skip = GithubTriggerV2Processor.skipTrigger(t, "pull_request", createPRPayload("pull_request", FORK_REPO));
        assertFalse(skip);
    }

    @Test
    void testSkipTriggerPRReviewCommentDifferentPRRepo() {
        TriggerEntry t = generateTrigger(true);

        boolean skip = GithubTriggerV2Processor.skipTrigger(t, "pull_request_review_comment", createPRPayload("pull_request_review_comment", FORK_REPO));
        assertTrue(skip);
    }

    @Test
    void testSkipTriggerPush() {
        TriggerEntry t = generateTrigger(true);
        Payload p = Payload.from("push", Map.of(
                "after", "123",
                "before", "456"
        ));

        boolean skip = GithubTriggerV2Processor.skipTrigger(t, "push", p);
        assertFalse(skip);
    }

    private static TriggerEntry generateTrigger(boolean useEventCommitId) {
        return new TriggerEntryBuilder()
                .eventSource("github")
                .id(UUID.randomUUID())
                .orgId(UUID.randomUUID())
                .orgName("mock-org")
                .projectId(UUID.randomUUID())
                .projectName("mock-proj")
                .repositoryId(UUID.randomUUID())
                .repositoryName("mock-repo")
                .cfg(Map.of("useEventCommitId", useEventCommitId))
                .build();
    }

    private static Payload createPRPayload(String eventName, String headUrl) {
        return Payload.from(eventName, Map.of(
                "pull_request", Map.of(
                        "base", Map.of(
                                "repo", Map.of(
                                        "clone_url", BASE_REPO
                                )
                        ),
                        "head", Map.of(
                                "repo", Map.of(
                                        "clone_url", headUrl
                                )
                        )
                )
        ));
    }

}
