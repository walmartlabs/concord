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
import { ConcordKey, RequestError } from '../../../api/common';
import { Button, Container, Form, Loader, Menu, Table } from 'semantic-ui-react';

import { FindUserField2, RequestErrorActivity } from '../../organisms';
import { UserEntry } from '../../../api/user';
import { TeamRoleDropdown } from '../../molecules';
import {
    listUsers as apiListUsers,
    addUsers as apiAddUsers,
    NewTeamUserEntry,
    TeamRole
} from '../../../api/org/team';

interface Entry extends NewTeamUserEntry {
    added?: boolean;
    deleted?: boolean;
    updated?: boolean;
}

interface Props {
    orgName: ConcordKey;
    teamName: ConcordKey;
}

const renderUser = (e: NewTeamUserEntry) => {
    if (!e.userDomain) {
        return e.displayName ? `${e.displayName} (${e.username})` : e.username;
    }

    return e.displayName
        ? `${e.displayName} (${e.username}@${e.userDomain})`
        : `${e.username}@${e.userDomain}`;
};

export default ({ orgName, teamName }: Props) => {
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

            let result = (await apiListUsers(orgName, teamName)).map((r) => ({ ...r }));
            setData(result.map((r) => ({ ...r })));
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
            setDirty(false);
        }
    }, [orgName, teamName]);

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

    const addMember = (u: UserEntry) => {
        setData((prev) => {
            const e: Entry = {
                added: true,
                userId: u.id,
                role: TeamRole.MEMBER,
                username: u.name,
                userDomain: u.domain,
                displayName: u.displayName
            };
            return [e, ...prev];
        });
    };

    const deleteMember = (idx: number) => {
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

    const undoMember = (idx: number) => {
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
            .map((e) => ({ userId: e.userId, role: e.role }));

        try {
            setSubmitting(true);
            await apiAddUsers(orgName, teamName, true, sanitizedData);
        } catch (e) {
            setError(e);
            return;
        } finally {
            setSubmitting(false);
        }

        load();
    }, [orgName, teamName, data, load]);

    if (loading) {
        return <Loader active={true} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <Menu secondary={true} widths={3}>
                {editMode && (
                    <Menu.Item disabled={submitting}>
                        <Container fluid={true} textAlign="left">
                            <Form>
                                <Form.Field>
                                    <FindUserField2
                                        placeholder="Add a team member"
                                        onSelect={(u) => addMember(u)}
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
                                    loading={loading || submitting}
                                    onClick={(ev) => save()}
                                />
                                <Button
                                    basic={true}
                                    negative={true}
                                    icon="cancel"
                                    content="Cancel"
                                    disabled={loading || submitting}
                                    onClick={() => cancel()}
                                />
                            </>
                        )}

                        {!editMode && (
                            <Button icon="edit" content="Edit" onClick={() => setEditMode(true)} />
                        )}
                    </Container>
                </Menu.Item>
            </Menu>

            {data.length === 0 && <h3>No team members.</h3>}

            {data.length > 0 && (
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Username</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Type</Table.HeaderCell>
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
                                <Table.Cell>{renderUser(e)}</Table.Cell>
                                <Table.Cell>{e.userType}</Table.Cell>
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
                                                e.deleted ? undoMember(idx) : deleteMember(idx)
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
