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
import { useState } from 'react';
import { Button } from 'semantic-ui-react';

import {ConcordId, ConcordKey, GenericOperationResult, RequestError} from '../../../api/common';
import { RequestErrorMessage, SingleOperationPopup } from '../../molecules';
import { deleteToken as apiDelete } from '../../../api/profile/api_token';

interface Props {
    id: ConcordId;
    name: ConcordKey;
    onDone: () => void;
}

export default ({ id, name, onDone }: Props) => {
    const [running, setRunning] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [response, setResponse] = useState<GenericOperationResult>();

    const postData = async () => {
        try {
            setError(undefined);
            setRunning(true);
            setResponse(await apiDelete(id));
        } catch (e) {
            setError(e);
        } finally {
            setRunning(false);
        }
    };

    return (
        <>
            {error && <RequestErrorMessage error={error} />}

            <SingleOperationPopup
                trigger={(onClick) => (
                    <Button negative={true} icon="delete" content="Delete" onClick={onClick} />
                )}
                title="Delete API Token?"
                introMsg={
                    <p>
                        Are you sure you want to delete the <b>{name}</b> API token?
                    </p>
                }
                running={running}
                runningMsg={<p>Removing the API Token...</p>}
                success={response ? response.ok : false}
                successMsg={<p>The API Token was removed successfully.</p>}
                error={error}
                onConfirm={postData}
                onDone={onDone}
            />
        </>
    );
};
