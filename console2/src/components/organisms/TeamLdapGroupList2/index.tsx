/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import { useCallback, useEffect, useState } from 'react';

import {
    addGroups as apiAddGroups,
    listGroups as apiListGroups,
    TeamGroupEntry,
    TeamRole
} from '../../../api/org/team';
import { ConcordKey, RequestError } from '../../../api/common';
import { Button, Container, Dropdown, Form, Loader, Menu, Table } from 'semantic-ui-react';
import { FindLdapGroupField, RequestErrorActivity } from '../../organisms';
import { AuthorizationSource, GroupRef, listAuthorizationSources } from '../../../api/service/console';
import { TeamRoleDropdown } from '../../molecules';

interface Entry extends TeamGroupEntry {
    added?: boolean;
    deleted?: boolean;
    updated?: boolean;
}

interface Props {
    orgName: ConcordKey;
    teamName: ConcordKey;
}

export default ({ orgName, teamName }: Props) => {
    const [sources, setSources] = useState<AuthorizationSource[]>([]);
    const [sourceId, setSourceId] = useState<string>();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<RequestError>();

    useEffect(() => {
        listAuthorizationSources().then((result) => {
            setSources(result);
            setSourceId(result.find((s) => s.sourceId === 'ldap')?.sourceId || result[0]?.sourceId);
        }).catch(setError).finally(() => setLoading(false));
    }, []);

    if (loading) {
        return <Loader active={true} />;
    }
    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            {sources.length === 0 && !error && <p>No authorization sources available.</p>}
            {sourceId && (
                <TeamGroups key={sourceId} orgName={orgName} teamName={teamName}
                    sourceId={sourceId} sources={sources} onSourceChange={setSourceId} />
            )}
        </>
    );
};

const TeamGroups = ({ orgName, teamName, sourceId, sources, onSourceChange }: Props & {
    sourceId: string;
    sources: AuthorizationSource[];
    onSourceChange: (sourceId: string) => void;
}) => {
    const [loading, setLoading] = useState(false);
    const [editMode, setEditMode] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [data, setData] = useState<Entry[]>([]);
    const [error, setError] = useState<RequestError>();
    const [dirty, setDirty] = useState(false);

    const load = useCallback(async () => {
        try {
            setLoading(true);
            setError(undefined);

            let result = (await apiListGroups(orgName, teamName, sourceId)).map((r) => ({ ...r }));
            setData(result.map((r) => ({ ...r })));
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
            setDirty(false);
        }
    }, [orgName, teamName, sourceId]);

    // initial load
    useEffect(() => {
        load();
    }, [load]);

    // set dirty to true on any changes
    useEffect(() => {
        setDirty(!!data.find((e) => e.added || e.deleted || e.updated));
    }, [data]);

    const cancel = () => {
        setEditMode(false);
        load();
    };

    const addGroup = (r: GroupRef) => {
        setData((prev) => {
            if (prev.some((entry) => entry.groupId === r.groupId)) {
                return prev.map((entry) => entry.groupId === r.groupId ? { ...entry, deleted: false } : entry);
            }
            const e: Entry = {
                added: true,
                role: TeamRole.MEMBER,
                groupId: r.groupId
            };
            return [e, ...prev];
        });
    };

    const deleteGroup = (idx: number) => {
        setData((prev) => {
            let e = prev[idx];

            if (e.added) {
                let result = [...prev];
                result.splice(idx, 1);
                return result;
            }

            e.deleted = true;
            e.updated = false;
            return [...prev];
        });
    };

    const undoGroup = (idx: number) => {
        setData((prev) => {
            prev[idx].deleted = false;
            return [...prev];
        });
    };

    const updateRole = (idx: number, role: TeamRole) => {
        setData((prev) => {
            let e = prev[idx];
            e.role = role;
            if (!e.deleted && !e.added) {
                e.updated = true;
            }
            return [...prev];
        });
    };

    const save = useCallback(async () => {
        const sanitizedData = data
            .filter((e) => !e.deleted)
            .map((e) => ({ groupId: e.groupId, role: e.role }));

        try {
            setSubmitting(true);
            await apiAddGroups(orgName, teamName, sourceId, true, sanitizedData);
        } catch (e) {
            setError(e);
            return;
        } finally {
            setSubmitting(false);
        }

        load();
    }, [orgName, teamName, sourceId, data, load]);

    if (loading) {
        return <Loader active={true} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <Form>
                <Form.Field>
                    <label>Authorization source</label>
                    <Dropdown selection={true} value={sourceId} disabled={editMode || submitting}
                        options={sources.map((s) => ({
                            key: s.sourceId, value: s.sourceId,
                            text: s.active ? s.sourceId : `${s.sourceId} (inactive)`
                        }))}
                        onChange={(e, d) => onSourceChange(d.value as string)} />
                </Form.Field>
            </Form>
            <Menu secondary={true} widths={3}>
                {editMode && (
                    <Menu.Item disabled={submitting}>
                        <Container fluid={true} textAlign="left">
                            <Form>
                                <Form.Field>
                                    <FindLdapGroupField
                                        sourceId={sourceId}
                                        placeholder="Add a team group"
                                        onSelect={addGroup}
                                    />
                                </Form.Field>
                            </Form>
                        </Container>
                    </Menu.Item>
                )}
                <Menu.Item position="right">
                    <Container textAlign="right">
                        {editMode && (
                            <>
                                <Button
                                    primary={true}
                                    content="Save changes"
                                    disabled={!dirty}
                                    loading={submitting}
                                    onClick={(ev) => save()}
                                />
                                <Button
                                    basic={true}
                                    negative={true}
                                    icon="cancel"
                                    content="Cancel"
                                    disabled={submitting}
                                    onClick={() => cancel()}
                                />
                            </>
                        )}

                        {!editMode && (
                            <Button icon="edit" content="Edit"
                                disabled={!sources.find((s) => s.sourceId === sourceId)?.active}
                                onClick={() => setEditMode(true)} />
                        )}
                    </Container>
                </Menu.Item>
            </Menu>

            {data.length === 0 && <h3>No team groups for this source.</h3>}

            {data.length > 0 && (
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Group</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Role</Table.HeaderCell>
                            {editMode && <Table.HeaderCell collapsing={true} />}
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {data.map((e, idx) => (
                            <Table.Row
                                key={idx}
                                negative={e.deleted}
                                positive={e.added}
                                warning={e.updated}>
                                <Table.Cell>{e.groupId}</Table.Cell>
                                <Table.Cell>
                                    {editMode ? (
                                        <TeamRoleDropdown
                                            value={e.role}
                                            disabled={submitting}
                                            onRoleChange={(value) => updateRole(idx, value)}
                                        />
                                    ) : (
                                        e.role
                                    )}
                                </Table.Cell>
                                {editMode && (
                                    <Table.Cell>
                                        <Button
                                            basic={true}
                                            negative={!e.deleted}
                                            icon={e.deleted ? 'undo' : 'delete'}
                                            disabled={submitting}
                                            onClick={() =>
                                                e.deleted ? undoGroup(idx) : deleteGroup(idx)
                                            }
                                        />
                                    </Table.Cell>
                                )}
                            </Table.Row>
                        ))}
                    </Table.Body>
                </Table>
            )}
        </>
    );
};
