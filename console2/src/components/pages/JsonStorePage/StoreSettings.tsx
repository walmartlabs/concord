/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { Divider, Header, Icon, Menu, Progress, Segment } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import {
    get as apiGet,
    getCapacity as apiGetCapacity,
    StorageCapacity,
    StorageEntry
} from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import StoreOwnerChangeActivity from './StoreOwnerChangeActivity';
import StoreDeleteActivity from './StoreDeleteActivity';
import StoreVisibilityActivity from './StoreVisibilityActivity';
import { formatFileSize } from '../../../utils';
import { LoadingDispatch } from '../../../App';
import EntityId from '../../molecules/EntityId';
import StoreOrganizationChangeActivity from "./StoreOrganizationChangeActivity";

export interface ExternalProps {
    orgName: ConcordKey;
    storeName: ConcordKey;
    forceRefresh: any;
}

const StoreSettings = ({ orgName, storeName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const fetchData = useCallback(async () => {
        const storage = await apiGet(orgName, storeName);
        const capacity = await apiGetCapacity(orgName, storeName);

        return { ...storage, ...capacity };
    }, [orgName, storeName]);

    const { data, error } = useApi<StorageEntry & StorageCapacity>(fetchData, {
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
            <Menu tabular={false} secondary={true} borderless={true}>
                <Menu.Item>
                    <Header as="h5" disabled={true}>
                        <EntityId id={data?.id} />
                    </Header>
                </Menu.Item>
            </Menu>

            <Segment>
                <Header as="h4" disabled={disabled}>
                    Capacity
                </Header>
                <Capacity current={data?.size} max={data?.maxSize} />
            </Segment>

            <Segment>
                <Header as="h4" disabled={disabled}>
                    Visibility
                </Header>
                <StoreVisibilityActivity
                    orgName={orgName}
                    storeName={storeName}
                    initialVisibility={data?.visibility}
                    disabled={disabled}
                />
            </Segment>

            <Divider horizontal={true} content="Danger Zone" disabled={disabled} />

            <Segment color="red" disabled={disabled}>
                <Header as="h4" disabled={disabled}>
                    JSON store owner
                </Header>
                <StoreOwnerChangeActivity
                    orgName={orgName}
                    storeName={storeName}
                    initialOwnerId={data?.owner?.id}
                    disabled={disabled}
                />

                <Header as="h4">Organization</Header>
                <StoreOrganizationChangeActivity
                    orgName={orgName}
                    storeName={storeName}
                    disabled={disabled}
                />

                <Header as="h4" disabled={disabled}>
                    Delete storage
                </Header>
                <StoreDeleteActivity orgName={orgName} storeName={storeName} disabled={disabled} />
            </Segment>
        </>
    );
};

interface CapacityProps {
    current?: number;
    max?: number;
}

const Capacity = ({ current, max }: CapacityProps) => {
    if (current === undefined || max === undefined) {
        return (
            <div>
                <em>No capacity limits configured</em>
            </div>
        );
    }

    return (
        <>
            <div>
                <Progress
                    percent={(current / max) * 100}
                    size={'tiny'}
                    color={'red'}
                    style={{ width: '30%' }}
                />
            </div>
            <Header size="tiny" style={{ marginTop: '0px', color: 'rgba(0, 0, 0, 0.5)' }}>
                <Icon name={'database'} color={'blue'} />
                Used {formatFileSize(current)} of {formatFileSize(max)}
            </Header>
        </>
    );
};

export default StoreSettings;
