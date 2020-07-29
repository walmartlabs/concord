/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import { ConcordKey } from '../../../api/common';
import { Divider, Header, Segment } from 'semantic-ui-react';
import { OrganizationOwnerChangeActivity, RequestErrorActivity } from '../index';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { get as apiGet, OrganizationEntry } from '../../../api/org';
import { useCallback } from 'react';
import EntityId from '../../molecules/EntityId';

interface ExternalProps {
    orgName: ConcordKey;
    forceRefresh: any;
}

const OrganizationSettings = ({ orgName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiGet(orgName);
    }, [orgName]);

    const { data, error } = useApi<OrganizationEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    const disabled = !data;

    return (
        <>
            <Header as="h5" disabled={true}>
                <EntityId id={data?.id} />
            </Header>

            <Divider horizontal={true} content="Danger Zone" disabled={disabled} />

            <Segment color="red" disabled={disabled}>
                <Header as="h4">Organization owner</Header>
                <OrganizationOwnerChangeActivity
                    orgId={data?.id}
                    initialOwnerId={data?.owner?.id}
                    disabled={disabled}
                />
            </Segment>
        </>
    );
};

export default OrganizationSettings;
