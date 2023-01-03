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
import { useContext, useState } from 'react';
import { Button, Icon, Message } from 'semantic-ui-react';
import { push as pushHistory } from 'connected-react-router';

import { RequestError } from '../../../api/common';
import { NewAPITokenForm, RequestErrorMessage, WithCopyToClipboard } from '../../molecules';
import {
    create as apiCreate,
    CreateApiKeyResult,
    NewTokenEntry
} from '../../../api/profile/api_token';
import { ReduxStore } from '../../../App';

const renderResponse = (response: CreateApiKeyResult, done: () => void, error?: RequestError) => {
    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    const { key } = response;

    return (
        <>
            <Message success={true}>
                <Message.Header>API Token created</Message.Header>
                <Message.Content>
                    <div>
                        <b>Token: </b>
                        <WithCopyToClipboard value={key}>
                            <span style={{ fontFamily: 'monospace' }}>{key}</span>
                        </WithCopyToClipboard>
                        <p>
                            <Icon color="black" name="info circle" />
                            <strong>Store this token for future use.</strong>
                        </p>
                    </div>
                </Message.Content>
            </Message>

            <Button primary={true} content={'Done'} onClick={() => done()} />
        </>
    );
};

export default () => {
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [response, setResponse] = useState<CreateApiKeyResult>();

    const postData = async (t: NewTokenEntry) => {
        try {
            setError(undefined);
            setSubmitting(true);
            setResponse(await apiCreate(t));
        } catch (e) {
            setError(e);
        } finally {
            setSubmitting(false);
        }
    };

    const store = useContext(ReduxStore);

    if (!error && response) {
        return renderResponse(
            response,
            () => store.dispatch(pushHistory(`/profile/api-token`)),
            error
        );
    }

    return (
        <>
            {error && <RequestErrorMessage error={error} />}

            <NewAPITokenForm
                submitting={submitting}
                onSubmit={(t) => postData(t)}
                initial={{ name: '' }}
            />
        </>
    );
};
