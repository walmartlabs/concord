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
    createOrUpdate as apiChangeOrganization
} from '../../../api/org/jsonstore';

import { ConcordKey, RequestError } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { Form, Input } from 'semantic-ui-react';
import { Redirect } from 'react-router';
import {FindOrganizationsField, RequestErrorActivity} from "../../organisms";

interface Props {
    orgName: ConcordKey;
    storeName: ConcordKey;
    disabled: boolean;
}

const StoreOrganizationChangeActivity = ({ orgName, storeName, disabled }: Props) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [state, setState] = useState<ConcordKey>(orgName);
    const [confirmation, setConfirmation] = useState('');
    const [error, setError] = useState<RequestError>();
    const [changing, setChanging] = useState<boolean>(false);
    const [success, setSuccess] = useState<boolean>(false);
    const [redirect, setRedirect] = useState<boolean>(false);

    const confirmHandler = useCallback(async () => {
        setChanging(true);

        try {
            const result = await apiChangeOrganization(
                orgName,
                storeName,
                undefined,
                state
            );
            setSuccess(result.ok);
        } catch (e) {
            setError(e);
        } finally {
            setChanging(false);
        }
    }, [state, orgName, storeName]);

    const redirectHandler = useCallback(() => {
        setRedirect((prevState) => !prevState);
    }, []);

    const resetHandler = useCallback(() => {
        setConfirmation('');
    }, []);

    if (redirect) {
        return <Redirect to={`/org/${state}/jsonstore/${storeName}`} />;
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
                        title="Move JSON Store?"
                        introMsg={
                            <>
                                <p>
                                    Are you sure you want to move the JSON Store to{' '}
                                    <strong>{state}</strong> organization?
                                </p>
                                <p>
                                    Please type <strong>{storeName}</strong> in the text box below
                                    to confirm.
                                </p>
                                <div
                                    className={`ui input ${
                                        confirmation !== storeName ? 'error' : ''
                                    }`}>
                                    <Input
                                        type="text"
                                        name="name"
                                        placeholder="JSON Store name"
                                        value={confirmation}
                                        onChange={(e, data) => setConfirmation(data.value)}
                                    />
                                </div>
                            </>
                        }
                        running={changing}
                        runningMsg={
                            <p>
                                Moving the JSON store <strong>{storeName}</strong> to{' '}
                                <strong>{state}</strong> organization...
                            </p>
                        }
                        success={success}
                        successMsg={
                            <p>
                                The JSON store <strong>{storeName}</strong> was moved successfully to{' '}
                                <strong>{state}</strong> organization.
                            </p>
                        }
                        error={error}
                        reset={resetHandler}
                        onConfirm={confirmHandler}
                        onDone={redirectHandler}
                        disableYes={confirmation !== storeName}
                    />
                </Form.Group>
            </Form>
        </>
    );
};

export default StoreOrganizationChangeActivity;