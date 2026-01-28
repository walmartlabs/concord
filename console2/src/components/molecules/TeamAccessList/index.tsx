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
import { Button, Container, Menu, Form, Table } from 'semantic-ui-react';
import { ResourceAccessLevel, ResourceAccessEntry } from '../../../api/org';
import { TeamAccessDropdown } from '../../molecules';
import { FindTeamDropdown } from '../../organisms';
import { ConcordKey } from '../../../api/common';
import { TeamEntry } from '../../../api/org/team';

interface Entry extends ResourceAccessEntry {
    added: boolean;
    deleted: boolean;
}

interface State {
    data: Entry[];
    dirty: boolean;
    editMode: boolean;
}

interface Props {
    data: ResourceAccessEntry[];
    submitting: boolean;
    orgName: ConcordKey;
    submit: (entries: ResourceAccessEntry[]) => void;
}

const toState = (data: ResourceAccessEntry[]): Entry[] =>
    data.map((e) => ({ ...e, added: false, deleted: false }));

class TeamAccessList extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { data: toState(props.data), dirty: false, editMode: false };
    }

    handleEditMode() {
        this.setState({ editMode: true });
    }

    handleCancelEdit() {
        this.setState({ data: toState(this.props.data), dirty: false, editMode: false });
    }

    handleRoleChange(idx: number, level: ResourceAccessLevel) {
        const { data } = this.state;
        data[idx].level = level;
        this.setState({ data, dirty: true });
    }

    handleDelete(idx: number) {
        const { data } = this.state;
        data[idx].deleted = !data[idx].deleted;
        this.setState({ data, dirty: true });
    }

    handleAddTeam(u: TeamEntry) {
        if (!u.name) {
            return;
        }
        const { data } = this.state;
        const e = {
            teamId: u.id,
            teamName: u.name,
            level: ResourceAccessLevel.READER,
            added: true,
            deleted: false
        };
        this.setState({ data: [e, ...data], dirty: true });
    }

    handleSave(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        this.setState({ editMode: false });
        const { data } = this.state;
        const { submit } = this.props;
        submit(data.filter((e) => !e.deleted));
    }
    componentDidUpdate(prevProps: Props) {
        if (prevProps !== this.props) {
            this.setState({ data: toState(this.props.data) });
        }
    }

    render() {
        const { data, editMode, dirty } = this.state;
        const { submitting, orgName } = this.props;
        return (
            <>
                <Menu secondary={true} widths={3}>
                    {editMode && (
                        <Menu.Item disabled={submitting}>
                            <Container fluid={true} textAlign="left">
                                <Form>
                                    <Form.Field>
                                        <FindTeamDropdown
                                            onSelect={(u: TeamEntry) => this.handleAddTeam(u)}
                                            orgName={orgName}
                                            name="teams"
                                            data-testid="team-access-add-dropdown"
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
                                        data-testid="team-access-save-btn"
                                    />
                                    <Button
                                        basic={true}
                                        negative={true}
                                        icon="cancel"
                                        content="Cancel"
                                        disabled={submitting}
                                        onClick={() => this.handleCancelEdit()}
                                        data-testid="team-access-cancel-btn"
                                    />
                                </>
                            )}

                            {!editMode && (
                                <Button
                                    icon="edit"
                                    content="Edit"
                                    onClick={() => this.handleEditMode()}
                                    data-testid="team-access-edit-btn"
                                />
                            )}
                        </Container>
                    </Menu.Item>
                </Menu>

                {data.length === 0 && <h3 data-testid="team-access-empty-message">No access rules defined.</h3>}
                {data.length > 0 && (
                    <Table data-testid="team-access-table">
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell>Team Name</Table.HeaderCell>
                                <Table.HeaderCell collapsing={true}>Access Level</Table.HeaderCell>
                                {editMode && <Table.HeaderCell collapsing={true} />}
                            </Table.Row>
                        </Table.Header>
                        <Table.Body>
                            {data.map((e, idx) => (
                                <Table.Row
                                    key={idx}
                                    negative={e.deleted}
                                    positive={e.added}
                                    data-testid={`team-access-row-${e.teamName}`}
                                >
                                    <Table.Cell data-testid={`team-name-${e.teamName}`}>{e.teamName}</Table.Cell>
                                    <Table.Cell data-testid={`team-access-level-${e.teamName}`}>
                                        {editMode ? (
                                            <TeamAccessDropdown
                                                value={e.level}
                                                disabled={submitting}
                                                onRoleChange={(value) =>
                                                    this.handleRoleChange(idx, value)
                                                }
                                                data-testid={`team-access-dropdown-${e.teamName}`}
                                            />
                                        ) : (
                                            e.level
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
                                                data-testid={`team-access-delete-btn-${e.teamName}`}
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

export default TeamAccessList;
