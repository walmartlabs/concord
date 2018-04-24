/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import React from 'react';
import { connect } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import {
    arrayPush as formArrayPush,
    change as formChange,
    FieldArray,
    reduxForm
} from 'redux-form';
import { Button, Divider, Form, Popup, Table, Message } from 'semantic-ui-react';
import { Checkbox, Dropdown, Field } from '../shared/forms';
import { actions as modal } from '../shared/Modal';
import * as RepositoryPopup from './RepositoryPopup';
import * as DeleteProjectPopup from './DeleteProjectPopup';
import * as StartProjectPopup from './StartProjectPopup/StartProjectPopup';
import * as RepositoryRefreshPopup from './RepositoryRefreshPopup';
import * as api from './api';
import GitUrlParse from 'git-url-parse';
import * as v from '../shared/validation';
import { actions } from './crud';
import { getCurrentOrg } from '../session/reducers';

const renderSourceText = (f, { commitId }) => (commitId ? 'Revision' : 'Branch/tag');

const renderRepositories = (
    pristine,
    newRepositoryPopupFn,
    editRepositoryPopupFn,
    startProcess,
    refreshRepo,
    refreshLoading,
    refreshResult,
    refreshError
) => ({ fields }) => {
    const newFn = (ev) => {
        ev.preventDefault();
        newRepositoryPopupFn();
    };

    const editFn = (idx) => (ev) => {
        ev.preventDefault();
        editRepositoryPopupFn(idx, fields.get(idx));
    };

    const refreshIcon = refreshError ? 'warning sign' : refreshResult ? 'check' : 'refresh';

    return (
        <Form.Field>
            <Button basic icon="add" onClick={newFn} content="Add repository" />

            <Divider horizontal />
            <div style={{ display: 'none', ...hideshow(fields) }}>
                <Message size="tiny" negative>
                    One or more secrets are defined in disabled secret store (and not available at
                    the moment). Please contact your administrator.
                </Message>
            </div>

            {fields.length > 0 && (
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing>Name</Table.HeaderCell>
                            <Table.HeaderCell>Git URL</Table.HeaderCell>
                            <Table.HeaderCell collapsing>Source</Table.HeaderCell>
                            <Table.HeaderCell collapsing>Path</Table.HeaderCell>
                            <Table.HeaderCell collapsing>Secret</Table.HeaderCell>
                            <Table.HeaderCell collapsing />
                            <Table.HeaderCell collapsing />
                            <Table.HeaderCell collapsing />
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {fields.map((f, idx) => (
                            <Table.Row key={idx}>
                                <Table.Cell>
                                    <a href="#edit" onClick={editFn(idx)}>
                                        {fields.get(idx).name}
                                    </a>
                                </Table.Cell>
                                <Table.Cell>
                                    <a href={GitUrlParse(fields.get(idx).url).toString('https')}>
                                        {fields.get(idx).url}
                                    </a>
                                </Table.Cell>
                                <Table.Cell>{renderSourceText(f, fields.get(idx))}</Table.Cell>
                                <Table.Cell>{fields.get(idx).path}</Table.Cell>
                                <Table.Cell style={{ ...getStripColor(fields.get(idx).enable) }}>
                                    {fields.get(idx).secretName}
                                </Table.Cell>
                                <Table.Cell>
                                    <Popup
                                        trigger={
                                            <Button
                                                negative
                                                icon="delete"
                                                onClick={() => fields.remove(idx)}
                                            />
                                        }
                                        content="Delete Repository"
                                        inverted
                                    />
                                </Table.Cell>
                                <Table.Cell>
                                    <Popup
                                        trigger={
                                            <Button
                                                primary
                                                content="Run"
                                                icon="chevron right"
                                                labelPosition="right"
                                                disabled={displayButton(
                                                    pristine,
                                                    fields.get(idx).enable
                                                )}
                                                onClick={startProcess(
                                                    fields.get(idx).name,
                                                    fields.get(idx).id
                                                )}
                                            />
                                        }
                                        content={`Start a new ${fields.get(idx).name} process`}
                                        inverted
                                    />
                                </Table.Cell>
                                <Table.Cell>
                                    <Popup
                                        trigger={
                                            <Button
                                                basic
                                                loading={refreshLoading}
                                                disabled={!pristine}
                                                positive={refreshResult}
                                                negative={refreshError && true}
                                                icon={refreshIcon}
                                                onClick={refreshRepo(fields.get(idx).name)}
                                            />
                                        }
                                        inverted
                                        content={`Refresh repository "${fields.get(idx).name}"`}
                                    />
                                </Table.Cell>
                            </Table.Row>
                        ))}
                    </Table.Body>
                </Table>
            )}
        </Form.Field>
    );
};

const getStripColor = (flag) => {
    if (flag !== undefined) {
        if (flag) {
            return { color: 'red' };
        }
    }
};

const hideshow = (fieldList) => {
    for (var i = 0; i < fieldList.length; i++) {
        if (fieldList.get(i).enable === true) {
            return { display: 'block' };
        }
    }
    return { display: 'none' };
};

const displayButton = (pristine, repositorySecretStoreEnable) => {
    const key = !pristine || repositorySecretStoreEnable;
    return key;
};

let projectForm = (props) => {
    const {
        handleSubmit,
        reset,
        createNew,
        newRepositoryPopupFn,
        editRepositoryPopupFn,
        deletePopupFn,
        startProcessFn,
        refreshRepoFn
    } = props;
    const { org, originalName } = props;
    const { pristine, submitting, invalid } = props;

    const { refreshLoading, refreshResult, refreshError } = props;

    const submitDisabled = pristine || submitting || invalid;
    const resetDisabled = pristine || submitting;
    const deleteDisabled = submitting || !originalName;

    const openDeletePopup = (ev) => {
        ev.preventDefault();
        deletePopupFn(org.name, originalName);
    };

    const startProcess = (repositoryName, repositoryId) => (ev) => {
        ev.preventDefault();
        startProcessFn(repositoryName, repositoryId);
    };

    const refreshRepo = (repositoryName) => (ev) => {
        ev.preventDefault();
        refreshRepoFn(org.name, originalName, repositoryName);
    };

    const visibilityOptions = [
        {
            text: 'Public',
            value: 'PUBLIC',
            description: 'Public projects can be used by any user to start a process.',
            icon: 'unlock'
        },
        {
            text: 'Private',
            value: 'PRIVATE',
            description: "Private projects can be used only by their organization's teams.",
            icon: 'lock'
        }
    ];

    return (
        <Form onSubmit={handleSubmit} loading={submitting}>
            {createNew && <Field name="name" label="Name" required />}

            <Dropdown name="visibility" label="Visibility" options={visibilityOptions} required />

            <Field name="description" label="Description" />

            <Popup
                trigger={
                    <Checkbox name="acceptsRawPayload" label="Accept payload archives" toggle />
                }
                content={
                    'Allows users to start new processes using payload archives. ' +
                    'When disabled, only the configured repositories can be used to start a new process.'
                }
                inverted
            />

            <Divider horizontal section>
                Repositories
            </Divider>
            <FieldArray
                name="repositories"
                component={renderRepositories(
                    pristine,
                    newRepositoryPopupFn,
                    editRepositoryPopupFn,
                    startProcess,
                    refreshRepo,
                    refreshLoading,
                    refreshResult,
                    refreshError
                )}
            />

            <Divider />

            <Button primary icon="save" content="Save" disabled={submitDisabled} />
            <Button content="Reset" onClick={reset} disabled={resetDisabled} />
            {!createNew && (
                <Button
                    floated="right"
                    negative
                    icon="delete"
                    content="Delete"
                    disabled={deleteDisabled}
                    onClick={openDeletePopup}
                />
            )}

            {!createNew && (
                <div>
                    {/* TODO: Add support for configuration objects
                    <Divider horizontal>Configuration</Divider>
                    <Message size="tiny" info>
                        Not supported yet. Please use the REST API to update the configuration
                        parameters.
                    </Message> */}
                </div>
            )}
        </Form>
    );
};

const validate = ({ name, description }) => {
    const errors = {};
    errors.name = v.project.name(name);
    errors.description = v.project.description(description);
    return errors;
};

const asyncValidate = ({ name }, dispatch, { originalName, org }) => {
    if (name === originalName) {
        return Promise.resolve(true);
    }

    return api.isProjectExists(org.name, name).then((exists) => {
        if (exists) {
            throw Object({ name: v.projectAlreadyExistsError(name) });
        }
    });
};

projectForm = reduxForm({
    form: 'project',
    validate,
    asyncValidate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(projectForm);

const mapStateToProps = ({ session }) => ({
    org: getCurrentOrg(session)
});

const mapDispatchToProps = (dispatch) => ({
    newRepositoryPopupFn: () => {
        // TODO replace with action creators
        const onSuccess = (data) => dispatch(formArrayPush('project', 'repositories', data));

        dispatch(modal.open(RepositoryPopup.MODAL_TYPE, { onSuccess }));
    },

    editRepositoryPopupFn: (idx, initialValues) => {
        // TODO replace with action creators
        const onSuccess = (data) => {
            dispatch(modal.close());
            dispatch(formChange('project', `repositories[${idx}]`, data));
        };

        dispatch(
            modal.open(RepositoryPopup.MODAL_TYPE, { onSuccess, initialValues, editMode: true })
        );
    },

    deletePopupFn: (orgName, projectName) => {
        const onConfirmFn = () => {
            dispatch(actions.deleteData([orgName, projectName], [modal.close()]));
            dispatch(pushHistory('/project/list'));
        };

        dispatch(modal.open(DeleteProjectPopup.MODAL_TYPE, { onConfirmFn }));
    },

    startProcessFn: (repositoryName, repositoryId) => {
        dispatch(modal.open(StartProjectPopup.MODAL_TYPE, { repositoryName, repositoryId }));
    },

    refreshRepoFn: (orgName, projectName, repositoryName) => {
        dispatch(
            modal.open(RepositoryRefreshPopup.MODAL_TYPE, { orgName, projectName, repositoryName })
        );
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(projectForm);
