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

import {ConcordKey, GenericOperationResult} from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import {useCallback, useState} from "react";
import {refreshRepository as apiRefreshRepo} from "../../../api/org/project/repository";
import {useApi} from "../../../hooks/useApi";

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
    onDone?: () => void;
}

const RefreshRepositoryPopup = (props: ExternalProps) => {
    const {orgName, projectName, repoName, trigger, onDone} = props;
    const [forceRequest, toggleForceRequest] = useState<boolean>(false);

    const refreshRepo = useCallback(async () => {
        return await apiRefreshRepo(orgName, projectName, repoName, true);
    }, [orgName, projectName, repoName]);

    const { data, error, clearState, isLoading } = useApi<GenericOperationResult>(refreshRepo, {
        fetchOnMount: false,
        forceRequest
    });

    const confirmHandler = useCallback(() => {
        toggleForceRequest((prevState) => !prevState);
    }, []);

    const resetHandler = useCallback(() => {
        clearState();
    }, [clearState]);

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Refresh repository?"
            introMsg={
                <p>
                    Refreshing the repository will update the Concord's cache and reload the
                    project's trigger definitions.
                </p>
            }
            running={isLoading}
            success={data !== undefined}
            successMsg={<p>The repository was refreshed successfully.</p>}
            error={error}
            onConfirm={confirmHandler}
            onDone={onDone}
            reset={resetHandler}
        />
    );
};

export default RefreshRepositoryPopup;