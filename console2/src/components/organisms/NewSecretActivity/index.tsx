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
import { Button, Message, TextArea } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { copyToClipboard } from '../../../clipboard';
import {
    CreateSecretResponse,
    SecretStoreType,
    SecretTypeExt,
    SecretVisibility,
    create as apiCreate,
} from '../../../api/org/secret';
import { useApi } from '../../../hooks/useApi';
import NewSecretForm, { NewSecretFormValues } from '../../molecules/NewSecretForm';

import './styles.css';
import { LoadingDispatch } from '../../../App';
import { useCallback, useState } from 'react';
import { RequestErrorActivity } from '../index';
import { useHistory } from '@/router';

interface ExternalProps {
    orgName: ConcordKey;
}

const INIT_VALUES: NewSecretFormValues = {
    name: '',
    visibility: SecretVisibility.PRIVATE,
    type: SecretTypeExt.NEW_KEY_PAIR,
    storeType: SecretStoreType.CONCORD,
};

const NewSecretActivity = ({ orgName }: ExternalProps) => {
    const history = useHistory();
    const dispatch = React.useContext(LoadingDispatch);
    const [values, setValues] = useState(INIT_VALUES);

    const postQuery = useCallback(() => apiCreate(orgName, values), [orgName, values]);

    const { error, isLoading, data, fetch } = useApi<CreateSecretResponse>(postQuery, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch,
    });

    const handleSubmit = useCallback(
        (submittedValues: NewSecretFormValues) => {
            setValues(submittedValues);
            fetch();
        },
        [fetch]
    );

    if (data) {
        const { publicKey, password } = data;

        return (
            <>
                <Message success={true}>
                    <Message.Header>Secret created</Message.Header>

                    {publicKey && (
                        <div>
                            <b>Public key: </b>
                            <Button
                                icon="copy"
                                size="mini"
                                basic={true}
                                onClick={() => copyToClipboard(publicKey)}
                            />
                            <TextArea className="secretData" value={publicKey} rows={5} />
                        </div>
                    )}

                    {password && (
                        <div>
                            <b>Export password: </b>
                            <Button
                                icon="copy"
                                size="mini"
                                basic={true}
                                onClick={() => copyToClipboard(password)}
                            />
                            <TextArea className="secretData" value={password} rows={2} />
                        </div>
                    )}
                </Message>

                <Button
                    primary={true}
                    content="Done"
                    onClick={() => history.push(`/org/${orgName}/secret/${values.name}`)}
                />
            </>
        );
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <NewSecretForm
                orgName={orgName}
                submitting={isLoading}
                onSubmit={handleSubmit}
                initial={INIT_VALUES}
            />
        </>
    );
};

export default NewSecretActivity;
