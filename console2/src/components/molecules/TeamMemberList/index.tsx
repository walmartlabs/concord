/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { Button, Container, Form, Menu, Table } from 'semantic-ui-react';

import { NewTeamUserEntry, TeamRole, TeamUserEntry } from '../../../api/org/team';
import { UserSearchResult } from '../../../api/service/console';
import { UserType } from '../../../api/user';
import { TeamRoleDropdown } from '../../molecules';
import { FindUserField } from '../../organisms';
import { renderUser } from '../../organisms/FindUserField';

interface Entry extends NewTeamUserEntry {
    added: boolean;
    deleted: boolean;
}

interface State {
    data: Entry[];
    dirty: boolean;
    editMode: boolean;
}

interface Props {
    data: TeamUserEntry[];
    submitting: boolean;
    submit: (users: NewTeamUserEntry[]) => void;
}

const toState = (data: TeamUserEntry[]): Entry[] =>
    data.map((e) => ({ ...e, added: false, deleted: false }));

class EntryList extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { data: toState(props.data), dirty: false, editMode: false };
    }

    componentDidUpdate(prevProps: Props) {
        if (this.props.data !== prevProps.data) {
            this.setState({ data: toState(this.props.data) });
        }
    }

    handleEditMode() {
        this.setState({ editMode: true });
    }

    handleCancelEdit() {
        this.setState({ data: toState(this.props.data), dirty: false, editMode: false });
    }

    handleRoleChange(idx: number, role: TeamRole) {
        const { data } = this.state;
        data[idx].role = role;
        this.setState({ data, dirty: true });
    }

    handleDelete(idx: number) {
        const { data } = this.state;
        data[idx].deleted = !data[idx].deleted;
        this.setState({ data, dirty: true });
    }

    handleAddUser(u: UserSearchResult) {
        const { data } = this.state;
        // TODO support for LOCAL users
        const e = {
            username: u.username,
            userDomain: u.userDomain,
            displayName: u.displayName,
            userType: UserType.LDAP,
            role: TeamRole.MEMBER,
            added: true,
            deleted: false
        };
        this.setState({ data: [e, ...data], dirty: true });
    }

    handleSave(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();

        const { data } = this.state;
        const { submit } = this.props;
        submit(data.filter((e) => !e.deleted));
    }

    render() {
        const { data, dirty, editMode } = this.state;
        const { submitting } = this.props;

        return (
            <>
                <Menu secondary={true} widths={3}>
                    {editMode && (
                        <Menu.Item disabled={submitting}>
                            <Container fluid={true} textAlign="left">
                                <Form>
                                    <Form.Field>
                                        <FindUserField
                                            placeholder="Add a team member"
                                            onSelect={(u: UserSearchResult) =>
                                                this.handleAddUser(u)
                                            }
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
                                        onClick={(ev) => this.handleSave(ev)}
                                    />
                                    <Button
                                        basic={true}
                                        negative={true}
                                        icon="cancel"
                                        content="Cancel"
                                        disabled={submitting}
                                        onClick={() => this.handleCancelEdit()}
                                    />
                                </>
                            )}

                            {!editMode && (
                                <Button
                                    icon="edit"
                                    content="Edit"
                                    onClick={() => this.handleEditMode()}
                                />
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
                                <Table.Row key={idx} negative={e.deleted} positive={e.added}>
                                    <Table.Cell>
                                        {renderUser(e.username, e.userDomain, e.displayName)}
                                    </Table.Cell>
                                    <Table.Cell>{e.userType}</Table.Cell>
                                    <Table.Cell>
                                        {editMode ? (
                                            <TeamRoleDropdown
                                                value={e.role}
                                                disabled={submitting}
                                                onRoleChange={(value) =>
                                                    this.handleRoleChange(idx, value)
                                                }
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
                                                onClick={() => this.handleDelete(idx)}
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
    }
}

export default EntryList;
