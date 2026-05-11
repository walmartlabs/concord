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
import { Loader } from 'semantic-ui-react';

import { ConcordKey, GenericOperationResult, RequestError } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';
import {
    getSecretAccess as apiGetSecretAccess,
    updateSecretAccess as apiUpdateSecretAccess
} from '../../../api/org/secret';
import { useApi } from '../../../hooks/useApi';
import { RequestErrorMessage, TeamAccessList } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
    onUpdated?: () => void;
}

const SecretTeamAccessActivity = ({ orgName, secretName, onUpdated }: ExternalProps) => {
    const [refresh, toggleRefresh] = React.useState(false);
    const [updateValue, setUpdateValue] = React.useState({
        access: [] as ResourceAccessEntry[]
    });

    const fetchData = React.useCallback(() => apiGetSecretAccess(orgName, secretName), [
        orgName,
        secretName
    ]);

    const { data, error, isLoading } = useApi<ResourceAccessEntry[]>(fetchData, {
        fetchOnMount: true,
        forceRequest: refresh
    });

    const postData = React.useCallback(async () => {
        const result = await apiUpdateSecretAccess(orgName, secretName, updateValue.access);
        toggleRefresh((prevState) => !prevState);
        onUpdated && onUpdated();
        return result;
    }, [onUpdated, orgName, secretName, updateValue]);

    const update = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true
    });

    const handleUpdate = React.useCallback(
        (entries: ResourceAccessEntry[]) => {
            setUpdateValue({ access: entries });
            update.fetch();
        },
        [update]
    );

    if (error) {
        return <RequestErrorMessage error={error as RequestError} />;
    }

    if (!data || isLoading || update.isLoading) {
        return <Loader active={true} />;
    }

    return (
        <div>
            <TeamAccessList
                data={data}
                submitting={update.isLoading}
                orgName={orgName}
                submit={handleUpdate}
            />
        </div>
    );
};

export default SecretTeamAccessActivity;
