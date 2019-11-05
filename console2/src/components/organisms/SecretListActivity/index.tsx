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
import { useCallback, useEffect, useState } from 'react';
import { Input, Menu } from 'semantic-ui-react';

import { ConcordKey, EntityType } from '../../../api/common';
import { SecretList } from '../../organisms';
import { checkResult as apiCheckResult } from '../../../api/org';
import { CreateNewEntityButton } from '../../molecules';

interface Props {
    orgName: ConcordKey;
}

const SecretListActivity = ({ orgName }: Props) => {
    const [filter, setFilter] = useState<string>();
    const [canCreate, setCanCreate] = useState<boolean>(false);

    const fetchCanCreateStatus = useCallback(async () => {
        const response = await apiCheckResult(EntityType.SECRET, orgName);
        setCanCreate(!!response);
    }, [orgName]);

    useEffect(() => {
        fetchCanCreateStatus();
    }, [orgName]);

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item>
                    <Input
                        icon="search"
                        placeholder="Filter..."
                        onChange={(ev, data) => setFilter(data.value)}
                    />
                </Menu.Item>

                <Menu.Item position={'right'}>
                    <CreateNewEntityButton
                        entity="secret"
                        orgName={orgName}
                        userInOrg={true}
                        enabledInPolicy={canCreate}
                    />
                </Menu.Item>
            </Menu>

            <SecretList orgName={orgName} filter={filter} />
        </>
    );
};

export default SecretListActivity;
