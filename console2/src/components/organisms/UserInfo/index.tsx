/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Wal-Mart Store, Inc.
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
import {
    List,
    ListContent,
    ListDescription,
    ListItem,
    Loader
} from 'semantic-ui-react';

import { get as apiGet, UserInfoEntry } from '../../../api/profile/user';
import { RequestErrorMessage } from '../../molecules';
import { useApi } from '../../../hooks/useApi';

export default () => {
    const { data, error, isLoading } = useApi<UserInfoEntry>(apiGet, {
        fetchOnMount: true
    });

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    if (isLoading) {
        return <Loader active={true} />;
    }

    if (!data) {
        return <p>There are no User info available</p>;
    }

    return (
        <div style={{padding: '.85714286em 1.14285714em'}}>
            <h5>Name</h5>
            <List divided={true} relaxed={true}>
                <ListItem>
                    <ListContent>
                        <ListDescription>{data.displayName}</ListDescription>
                    </ListContent>
                </ListItem>
            </List>


            <h5>Roles</h5>
            <List divided={true} relaxed={true}>
                {data.roles && data.roles.map((role, index) => (
                    <ListItem key={index}>
                        <ListContent>
                            <ListDescription>{role}</ListDescription>
                        </ListContent>
                    </ListItem>
                ))}
            </List>

            <h5>Teams</h5>
            <List divided={true} relaxed={true}>
                {data.teams && data.teams.map((team, index) => (
                    <ListItem key={index}>
                        <ListContent>
                            <ListDescription>{team.orgName} / {team.teamName} ({team.role})</ListDescription>
                        </ListContent>
                    </ListItem>
                ))}
            </List>
        </div>
    );
};
