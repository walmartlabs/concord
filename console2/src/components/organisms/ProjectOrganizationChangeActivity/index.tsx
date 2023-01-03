/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import { ConcordKey, RequestError } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { Form, Input } from 'semantic-ui-react';
import { FindOrganizationsField, RequestErrorActivity } from '../index';
import { Redirect } from 'react-router';

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    disabled: boolean;
}

export default ({ orgName, projectName, disabled }: Props) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [state, setState] = useState<ConcordKey>(orgName);
    const [confirmation, setConfirmation] = useState('');
    const [error, setError] = useState<RequestError>();
    const [changing, setChanging] = useState<boolean>(false);
    const [success, setSuccess] = useState<boolean>(false);
    const [redirect, setRedirect] = useState<boolean>(false);

    const toUpdateProjectEntry = (orgName: string, projectName: string): UpdateProjectEntry => {
        return {
            name: projectName,
            orgName
        };
    };

    const confirmHandler = useCallback(async () => {
        setChanging(true);

        try {
            const result = await apiChangeOrganization(
                orgName,
                toUpdateProjectEntry(state, projectName)
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

    const resetHandler = useCallback(() => {
        setConfirmation('');
    }, []);

    if (redirect) {
        return <Redirect to={`/org/${state}/project/${projectName}`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <Form loading={changing}>
                <Form.Group widths={3}>
                    <Form.Field disabled={disabled}>
                        <FindOrganizationsField
                            placeholder="Search for an organization..."
                            defaultOrgName={orgName}
                            required={true}
                            onReset={() => {
                                setDirty(false);
                                setState(orgName);
                            }}
                            onSelect={(value) => {
                                setDirty(true);
                                setState(value.name);
                            }}
                        />
                    </Form.Field>
                    <SingleOperationPopup
                        trigger={(onClick) => (
                            <Form.Button
                                primary={true}
                                negative={true}
                                content="Move"
                                disabled={!dirty || disabled}
                                onClick={onClick}
                            />
                        )}
                        title="Move project?"
                        introMsg={
                            <>
                                <p>
                                    Are you sure you want to move the project to{' '}
                                    <strong>{state}</strong> organization?
                                </p>
                                <ul>
                                    <li>
                                        Any secret used by repositories in this project will not be
                                        available.
                                    </li>
                                    <li>
                                        Any secrets scoped to this project, the mapping will be
                                        removed.{' '}
                                    </li>
                                </ul>
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
                                <strong>{state}</strong> organization...
                            </p>
                        }
                        success={success}
                        successMsg={
                            <p>
                                The project <strong>{projectName}</strong> was moved successfully to{' '}
                                <strong>{state}</strong> organization.
                            </p>
                        }
                        error={error}
                        reset={resetHandler}
                        onConfirm={confirmHandler}
                        onDone={redirectHandler}
                        disableYes={confirmation !== projectName}
                    />
                </Form.Group>
            </Form>
        </>
    );
};
