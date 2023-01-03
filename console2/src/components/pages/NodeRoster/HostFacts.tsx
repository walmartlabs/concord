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
import { useCallback } from 'react';
import ReactJson from 'react-json-view';

import { ConcordKey } from '../../../api/common';
import { getLatestHostFacts as apiGet } from '../../../api/noderoster';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import { LoadingDispatch } from '../../../App';

export interface ExternalProps {
    hostId: ConcordKey;
    forceRefresh: any;
}

const HostFacts = ({ hostId, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiGet(hostId);
    }, [hostId]);

    const { data, error } = useApi<Object>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <ReactJson
            src={data === undefined ? {} : data}
            collapsed={false}
            name={null}
            enableClipboard={true}
            displayObjectSize={false}
            displayDataTypes={false}
            style={data === undefined ? { opacity: 0.4 } : { overflow: 'auto' }}
        />
    );
};

export default HostFacts;
