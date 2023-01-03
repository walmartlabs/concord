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
import { Input } from 'semantic-ui-react';

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { deleteRepository as apiRepoDelete } from '../../../api/org/project/repository';
import { useApi } from '../../../hooks/useApi';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
    onDone: () => void;
}

const DeleteRepositoryPopup = (props: ExternalProps) => {
    const { orgName, projectName, repoName, trigger, onDone } = props;

    const [confirmation, setConfirmation] = useState('');

    const deleteDataRequest = useCallback(() => {
        return apiRepoDelete(orgName, projectName, repoName);
    }, [orgName, projectName, repoName]);

    const { data, isLoading, error, clearState, fetch } = useApi<GenericOperationResult>(
        deleteDataRequest,
        { fetchOnMount: false, requestByFetch: true }
    );

    const resetHandler = useCallback(() => {
        clearState();
    }, [clearState]);

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Delete repository?"
            introMsg={
                <>
                    <p>
                        Are you sure you want to delete the repository? Any process or repository
                        that uses this repository may stop working correctly.
                    </p>
                    <p>
                        Please type <strong>{repoName}</strong> to confirm.
                    </p>
                    <div className={`ui input ${confirmation !== repoName ? 'error' : ''}`}>
                        <Input
                            type="text"
                            name="name"
                            placeholder="Repository name"
                            value={confirmation}
                            onChange={(e, data) => setConfirmation(data.value)}
                        />
                    </div>
                </>
            }
            running={isLoading}
            runningMsg={<p>Removing the repository...</p>}
            success={data !== undefined}
            successMsg={<p>The repository was removed successfully.</p>}
            error={error}
            reset={resetHandler}
            onConfirm={fetch}
            onDone={onDone}
            disableYes={confirmation !== repoName}
        />
    );
};

export default DeleteRepositoryPopup;
