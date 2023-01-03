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
import { Icon, List, Loader } from 'semantic-ui-react';

import { APITokenDeleteActivity } from '../../organisms';
import { list as apiList, TokenEntry } from '../../../api/profile/api_token';
import { LocalTimestamp, RequestErrorMessage } from '../../molecules';
import { useApi } from '../../../hooks/useApi';

export default () => {
    const { data, error, isLoading, fetch } = useApi<TokenEntry[]>(apiList, {
        fetchOnMount: true
    });

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    if (isLoading) {
        return <Loader active={true} />;
    }

    if (!data || data.length === 0) {
        return <p>There are no API tokens associated with your account.</p>;
    }

    return (
        <List divided={true} relaxed={true} size="large">
            {data.map((token, index) => (
                <List.Item key={index}>
                    <List.Content floated={'right'}>
                        <APITokenDeleteActivity
                            id={token.id}
                            name={token.name}
                            onDone={() => fetch()}
                        />
                    </List.Content>
                    <Icon name="key" size="large" />
                    <List.Content>
                        <List.Header>{token.name}</List.Header>
                        {token.expiredAt && (
                            <List.Description>
                                expired at: <LocalTimestamp value={token.expiredAt} />
                            </List.Description>
                        )}
                    </List.Content>
                </List.Item>
            ))}
        </List>
    );
};
