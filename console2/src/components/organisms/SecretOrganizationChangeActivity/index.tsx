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

import { changeOrganization as apiChangeOrganization } from '../../../api/org/secret';

import { ConcordId, RequestError } from '../../../api/common';
import { RequestErrorMessage, SingleOperationPopup } from '../../molecules';
import { OrganizationEntry, OrganizationVisibility } from '../../../api/org';
import { Form, Input } from 'semantic-ui-react';
import { FindOrganizationsField } from '../index';
import { Redirect } from 'react-router';

interface Props {
    orgName: string;
    secretName: string;
    orgId: ConcordId;
}

export default ({ orgId, orgName, secretName }: Props) => {
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
    const [reset, setReset] = useState<boolean>(false);

    const onSelect = (o: OrganizationEntry) => {
        setState(o);
        setDirty(orgName !== o.name);
    };

    const confirmHandler = useCallback(async () => {
        setChanging(true);
        try {
            const result = await apiChangeOrganization(orgName, secretName, state.id);
            setSuccess(result.ok);
        } catch (e) {
            setError(e);
        } finally {
            setChanging(false);
        }
    }, [state, orgName, secretName]);

    const redirectHandler = useCallback(() => {
        setRedirect((prevState) => !prevState);
    }, []);

    const resetHandler = useCallback(() => {
        setReset((prevState) => !prevState);
    }, []);

    if (redirect) {
        return <Redirect to={`/org/${state.name}/secret/${secretName}`} />;
    }

    if (reset) {
        return <Redirect to={`/org/${orgName}/secret/${secretName}`} />;
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
                        title="Move secret?"
                        introMsg={
                            <>
                                <p>
                                    Are you sure you want to move the secret to{' '}
                                    <strong>{state.name}</strong> organization?
                                    <ul>
                                        <li>
                                            Any repositories using this secret in this organization,
                                            the mapping will be removed{' '}
                                        </li>
                                        <li>
                                            If this secret is scoped to a project, the mapping will
                                            be removed.{' '}
                                        </li>
                                    </ul>
                                </p>
                                <p>
                                    Please type <strong>{secretName}</strong> to confirm.
                                </p>
                                <div
                                    className={`ui input ${
                                        confirmation !== secretName ? 'error' : ''
                                    }`}>
                                    <Input
                                        type="text"
                                        name="name"
                                        placeholder="Secret name"
                                        value={confirmation}
                                        onChange={(e, data) => setConfirmation(data.value)}
                                    />
                                </div>
                            </>
                        }
                        running={changing}
                        runningMsg={
                            <p>
                                Moving the secret <strong>{secretName}</strong> to{' '}
                                <strong>{state.name}</strong> organization...
                            </p>
                        }
                        success={success}
                        successMsg={
                            <p>
                                The secret <strong>{secretName}</strong> was moved successfully to{' '}
                                <strong>{state.name}</strong> organization.
                            </p>
                        }
                        error={error}
                        reset={resetHandler}
                        onConfirm={confirmHandler}
                        onDone={redirectHandler}
                        disableYes={confirmation !== secretName}
                    />
                </Form.Group>
            </Form>
        </>
    );
};
