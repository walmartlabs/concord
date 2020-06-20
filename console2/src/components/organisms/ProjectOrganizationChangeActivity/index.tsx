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
import { useCallback, useState } from 'react';

import {
    createOrUpdate as apiChangeOrganization,
    UpdateProjectEntry
} from '../../../api/org/project';

import { ConcordId, RequestError } from '../../../api/common';
import { RequestErrorMessage, SingleOperationPopup } from '../../molecules';
import { OrganizationEntry, OrganizationVisibility } from '../../../api/org';
import { Form, Input } from 'semantic-ui-react';
import { FindOrganizationsField } from '../index';
import { Redirect } from 'react-router';

interface Props {
    orgName: string;
    projectName: string;
    orgId: ConcordId;
}

export default ({ orgName, orgId, projectName }: Props) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [state, setState] = useState<OrganizationEntry>({
        id: orgId,
        name: orgName,
        visibility: OrganizationVisibility.PRIVATE
    });
    const [confirmation, setConfirmation] = useState('');
    const [error, setError] = useState<RequestError>();
    const [changing, setChanging] = useState<boolean>(false);
    const [success, setSuccess] = useState<boolean>(false);
    const [redirect, setRedirect] = useState<boolean>(false);

    const onSelect = (o: OrganizationEntry) => {
        setState(o);
        setDirty(orgName !== o.name);
    };

    const toUpdateProjectEntry = (orgId: string, projectName: string): UpdateProjectEntry => {
        return {
            name: projectName,
            orgId: orgId
        };
    };

    const confirmHandler = useCallback(async () => {
        setChanging(true);

        try {
            const result = await apiChangeOrganization(
                orgName,
                toUpdateProjectEntry(state.id, projectName)
            );
            setSuccess(result.ok);
        } catch (e) {
            setError(e);
        } finally {
            setChanging(false);
        }
    }, [state, orgName, projectName]);

    const redirectHandler = useCallback(() => {
        setRedirect((prevState) => !prevState);
    }, []);

    if (redirect) {
        return <Redirect to={`/org/${state.name}/project/${projectName}`} />;
    }

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <Form loading={changing}>
                <Form.Group widths={3}>
                    <Form.Field>
                        <FindOrganizationsField
                            placeholder="Search for an organization..."
                            defaultValue={orgName || ''}
                            onSelect={(u: any) => onSelect(u)}
                        />
                    </Form.Field>
                    <SingleOperationPopup
                        trigger={(onClick) => (
                            <Form.Button
                                primary={true}
                                negative={true}
                                content="Move"
                                disabled={!dirty}
                                onClick={onClick}
                            />
                        )}
                        title="Move project?"
                        introMsg={
                            <>
                                <p>
                                    Are you sure you want to move the project to{' '}
                                    <strong>{state.name}</strong> organization?
                                    <ul>
                                        <li>
                                            Any secret used by repositories in this project will not
                                            be available.
                                        </li>
                                        <li>
                                            Any secrets scoped to this project, the mapping will be
                                            removed.{' '}
                                        </li>
                                    </ul>
                                </p>
                                <p>
                                    <strong>NOTE:</strong> Move the secrets to the same organization
                                    as this project, and map them to repositories or projects again
                                    to use those secrets.
                                </p>
                                <p>
                                    Please type <strong>{projectName}</strong> in the text box below
                                    to confirm.
                                </p>
                                <div
                                    className={`ui input ${
                                        confirmation !== projectName ? 'error' : ''
                                    }`}>
                                    <Input
                                        type="text"
                                        name="name"
                                        placeholder="Project name"
                                        value={confirmation}
                                        onChange={(e, data) => setConfirmation(data.value)}
                                    />
                                </div>
                            </>
                        }
                        running={changing}
                        runningMsg={
                            <p>
                                Moving the project <strong>{projectName}</strong> to{' '}
                                <strong>{state.name}</strong> organization...
                            </p>
                        }
                        success={success}
                        successMsg={
                            <p>
                                The project <strong>{projectName}</strong> was moved successfully to{' '}
                                <strong>{state.name}</strong> organization.
                            </p>
                        }
                        error={error}
                        onConfirm={confirmHandler}
                        onDone={redirectHandler}
                        disableYes={confirmation !== projectName}
                    />
                </Form.Group>
            </Form>
        </>
    );
};
