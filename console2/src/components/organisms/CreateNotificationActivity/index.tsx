/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import * as React from 'react';
import { useEffect, useState } from 'react';
import { Button, Checkbox, Dropdown, Form, Header, Input, Message, TextArea } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { get as getOrg, OrganizationEntry } from '../../../api/org';
import { get as getProject } from '../../../api/org/project';
import { get as getRepo } from '../../../api/org/project/repository';
import { get as getUserInfo } from '../../../api/profile/user';
import { list as listUsers, UserEntry } from '../../../api/user';
import { createNotification } from '../../../api/notifications';
import FindOrganizationsField from '../FindOrganizationsField';
import FindUserField2 from '../FindUserField2';

type OwnerType = 'ORG' | 'PROJECT' | 'USER';

interface Props {
    defaultOwnerType?: OwnerType;
    defaultOrgName?: string;
    defaultProjectName?: string;
    defaultRepoName?: string;
    /** Username of the process initiator; used for USER-owner defaults on payload processes. */
    defaultInitiatorUsername?: string;
}

interface FormErrors {
    summary?: string;
    body?: string;
    actionLink?: string;
    org?: string;
    project?: string;
    repo?: string;
    user?: string;
}

const OWNER_TYPE_OPTIONS = [
    { key: 'ORG', text: 'Organization', value: 'ORG' },
    { key: 'PROJECT', text: 'Project', value: 'PROJECT' },
    { key: 'USER', text: 'User', value: 'USER' },
];


const CONCORD_KEY_RE = /^[0-9a-zA-Z][0-9a-zA-Z_@.\-~]{2,127}$/;
const CONCORD_KEY_MESSAGE =
    'Must start with a letter or digit and be 3–128 characters containing only letters, digits, _, @, ., -, or ~.';

const MAX_SUMMARY_LENGTH = 255;
const MAX_BODY_LENGTH = 4096;

const CreateNotificationActivity: React.FC<Props> = ({
    defaultOwnerType,
    defaultOrgName,
    defaultProjectName,
    defaultRepoName,
    defaultInitiatorUsername,
}) => {
    // null = loading, true/false = resolved
    const [canNotify, setCanNotify] = useState<boolean | null>(null);

    // Form fields
    const [ownerType, setOwnerType] = useState<OwnerType>(defaultOwnerType ?? 'ORG');
    const [summary, setSummary] = useState('');
    const [body, setBody] = useState('');
    const [actionLink, setActionLink] = useState('');
    const [triggerEmail, setTriggerEmail] = useState(false);

    // Owner entity state
    const [selectedOrg, setSelectedOrg] = useState<OrganizationEntry | null>(null);
    const [projectNameValue, setProjectNameValue] = useState(defaultProjectName ?? '');
    const [repoNameValue, setRepoNameValue] = useState(defaultRepoName ?? '');
    const [selectedUser, setSelectedUser] = useState<UserEntry | null>(null);
    const [defaultUserId, setDefaultUserId] = useState<ConcordId | undefined>(undefined);

    // Validation & submission state
    const [errors, setErrors] = useState<FormErrors>({});
    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [submitSuccess, setSubmitSuccess] = useState(false);

    // Check whether the current user has admin or moderator role
    useEffect(() => {
        getUserInfo()
            .then((info) => {
                const allowed =
                    info.roles?.some(
                        (r) => r === 'concordAdmin' || r === 'concordModerator'
                    ) ?? false;
                setCanNotify(allowed);
            })
            .catch(() => setCanNotify(false));
    }, []);

    // Resolve initiator username → UUID so FindUserField2 can pre-populate
    useEffect(() => {
        if (!defaultInitiatorUsername) return;
        listUsers(0, 10, defaultInitiatorUsername)
            .then(({ items }) => {
                const exact = items.find((u) => u.name === defaultInitiatorUsername);
                if (exact) setDefaultUserId(exact.id);
            })
            .catch(() => {});
    }, [defaultInitiatorUsername]);

    // Pre-populate selectedOrg from context whenever ownerType changes to ORG or PROJECT
    useEffect(() => {
        if (ownerType !== 'ORG' && ownerType !== 'PROJECT') return;
        if (!defaultOrgName) return;
        getOrg(defaultOrgName)
            .then((org) => setSelectedOrg(org))
            .catch(() => {});
    }, [ownerType, defaultOrgName]);

    const clearError = (field: keyof FormErrors) =>
        setErrors((prev) => ({ ...prev, [field]: undefined }));

    const handleOwnerTypeChange = (newType: OwnerType) => {
        setOwnerType(newType);
        setSelectedOrg(null);
        setProjectNameValue(newType === 'PROJECT' ? (defaultProjectName ?? '') : '');
        setRepoNameValue(newType === 'PROJECT' ? (defaultRepoName ?? '') : '');
        setSelectedUser(null);
        setErrors({});
    };

    const validate = (): FormErrors => {
        const e: FormErrors = {};

        const trimmedSummary = summary.trim();
        if (!trimmedSummary) {
            e.summary = 'Summary is required.';
        } else if (trimmedSummary.length > MAX_SUMMARY_LENGTH) {
            e.summary = `Summary must be ${MAX_SUMMARY_LENGTH} characters or fewer (currently ${trimmedSummary.length}).`;
        }

        if (body.trim().length > MAX_BODY_LENGTH) {
            e.body = `Body must be ${MAX_BODY_LENGTH} characters or fewer (currently ${body.trim().length}).`;
        }

        const trimmedLink = actionLink.trim();
        if (trimmedLink && !/^https?:\/\/.+/.test(trimmedLink)) {
            e.actionLink = 'Action link must start with http:// or https://.';
        }

        if (ownerType === 'ORG' || ownerType === 'PROJECT') {
            if (!selectedOrg) {
                e.org = 'Please select an organization.';
            }
        }

        if (ownerType === 'PROJECT') {
            const trimmedProject = projectNameValue.trim();
            if (!trimmedProject) {
                e.project = 'Project name is required.';
            } else if (!CONCORD_KEY_RE.test(trimmedProject)) {
                e.project = `Invalid project name. ${CONCORD_KEY_MESSAGE}`;
            }

            const trimmedRepo = repoNameValue.trim();
            if (trimmedRepo && !CONCORD_KEY_RE.test(trimmedRepo)) {
                e.repo = `Invalid repository name. ${CONCORD_KEY_MESSAGE}`;
            }
        }

        if (ownerType === 'USER' && !selectedUser) {
            e.user = 'Please select a user.';
        }

        return e;
    };

    const handleSubmit = async () => {
        setSubmitError(null);
        setSubmitSuccess(false);

        const validationErrors = validate();
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }
        setErrors({});

        setSubmitting(true);
        try {
            let userId: ConcordId | undefined;
            let orgId: ConcordId | undefined;
            let projectId: ConcordId | undefined;
            let repoId: ConcordId | undefined;

            if (ownerType === 'ORG') {
                orgId = selectedOrg!.id;
            } else if (ownerType === 'PROJECT') {
                const projectEntry = await getProject(
                    selectedOrg!.name,
                    projectNameValue.trim()
                );
                projectId = projectEntry.id;
                if (repoNameValue.trim()) {
                    const repoEntry = await getRepo(
                        selectedOrg!.name,
                        projectNameValue.trim(),
                        repoNameValue.trim()
                    );
                    repoId = repoEntry.id;
                }
            } else {
                userId = selectedUser!.id;
            }

            await createNotification({
                userId,
                orgId,
                projectId,
                repoId,
                summary: summary.trim(),
                body: body.trim(),
                actionLink: actionLink.trim(),
                triggerEmail,
            });

            setSubmitSuccess(true);
        } catch (e: any) {
            // managedFetch throws RequestErrorData, not a standard Error.
            // `details` carries the parsed response body; `message` is a fallback.
            const raw: string = e?.details || e?.message || '';
            let displayMessage: string;
            try {
                // The body might itself be a JSON string (e.g. text/plain response
                // that happens to contain JSON); try to extract a readable message.
                const parsed = JSON.parse(raw);
                displayMessage =
                    parsed?.message || parsed?.details || parsed?.[0]?.message || raw;
            } catch {
                // Not JSON — use the body text directly.
                displayMessage = raw || 'An error occurred while creating the notification.';
            }
            setSubmitError(displayMessage);
        } finally {
            setSubmitting(false);
        }
    };

    // Still loading roles — render nothing to avoid a NotFound flash on deep-link
    if (canNotify === null) {
        return null;
    }

    // Not authorized — render nothing (tab not shown to unauthorized users anyway)
    if (!canNotify) {
        return null;
    }

    return (
        <div style={{ maxWidth: '600px', padding: '1em' }}>
            <Header as="h4">Create Notification</Header>

            {submitSuccess && (
                <Message positive={true} onDismiss={() => setSubmitSuccess(false)}>
                    Notification created successfully.
                </Message>
            )}

            {submitError && (
                <Message negative={true} onDismiss={() => setSubmitError(null)}>
                    {submitError}
                </Message>
            )}

            <Form>
                <Form.Field required={true} error={!!errors.summary}>
                    <label>Summary</label>
                    <Input
                        value={summary}
                        onChange={(_, { value }) => {
                            setSummary(value);
                            clearError('summary');
                        }}
                        placeholder="Brief description"
                        fluid={true}
                    />
                    {errors.summary && (
                        <div className="ui pointing red basic label">{errors.summary}</div>
                    )}
                </Form.Field>

                <Form.Field error={!!errors.body}>
                    <label>Body</label>
                    <TextArea
                        value={body}
                        onChange={(_, { value }) => {
                            setBody(value as string);
                            clearError('body');
                        }}
                        placeholder="Notification body"
                        rows={4}
                    />
                    {errors.body && (
                        <div className="ui pointing red basic label">{errors.body}</div>
                    )}
                </Form.Field>

                <Form.Field error={!!errors.actionLink}>
                    <label>Action Link</label>
                    <Input
                        value={actionLink}
                        onChange={(_, { value }) => {
                            setActionLink(value);
                            clearError('actionLink');
                        }}
                        placeholder="https://..."
                        fluid={true}
                    />
                    {errors.actionLink && (
                        <div className="ui pointing red basic label">{errors.actionLink}</div>
                    )}
                </Form.Field>

                <Form.Field>
                    <Checkbox
                        label="Trigger email"
                        checked={triggerEmail}
                        onChange={(_, { checked }) => setTriggerEmail(!!checked)}
                    />
                </Form.Field>

                <Form.Field>
                    <label>Owner Type</label>
                    <Dropdown
                        selection={true}
                        options={OWNER_TYPE_OPTIONS}
                        value={ownerType}
                        onChange={(_, { value }) => handleOwnerTypeChange(value as OwnerType)}
                    />
                </Form.Field>

                {(ownerType === 'ORG' || ownerType === 'PROJECT') && (
                    <Form.Field required={true} error={!!errors.org}>
                        <label>Organization</label>
                        <FindOrganizationsField
                            key={`org-field-${ownerType}`}
                            defaultOrgName={defaultOrgName}
                            placeholder="Search organizations..."
                            onSelect={(org) => { setSelectedOrg(org); clearError('org'); }}
                            onReset={(org) => { setSelectedOrg(org ?? null); clearError('org'); }}
                            onClear={() => setSelectedOrg(null)}
                        />
                        {errors.org && (
                            <div className="ui pointing red basic label">{errors.org}</div>
                        )}
                    </Form.Field>
                )}

                {ownerType === 'PROJECT' && (
                    <>
                        <Form.Field required={true} error={!!errors.project}>
                            <label>Project</label>
                            <Input
                                value={projectNameValue}
                                onChange={(_, { value }) => {
                                    setProjectNameValue(value);
                                    clearError('project');
                                }}
                                placeholder="Project name"
                                fluid={true}
                            />
                            {errors.project && (
                                <div className="ui pointing red basic label">{errors.project}</div>
                            )}
                        </Form.Field>
                        <Form.Field error={!!errors.repo}>
                            <label>Repository (optional)</label>
                            <Input
                                value={repoNameValue}
                                onChange={(_, { value }) => {
                                    setRepoNameValue(value);
                                    clearError('repo');
                                }}
                                placeholder="Repository name"
                                fluid={true}
                            />
                            {errors.repo && (
                                <div className="ui pointing red basic label">{errors.repo}</div>
                            )}
                        </Form.Field>
                    </>
                )}

                {ownerType === 'USER' && (
                    <Form.Field required={true} error={!!errors.user}>
                        <label>User</label>
                        <FindUserField2
                            defaultUserId={defaultUserId}
                            onSelect={(user) => { setSelectedUser(user); clearError('user'); }}
                            placeholder="Search users..."
                        />
                        {errors.user && (
                            <div className="ui pointing red basic label">{errors.user}</div>
                        )}
                    </Form.Field>
                )}

                <Button
                    primary={true}
                    type="button"
                    onClick={handleSubmit}
                    loading={submitting}
                    disabled={submitting}>
                    Create Notification
                </Button>
            </Form>
        </div>
    );
};

export default CreateNotificationActivity;
