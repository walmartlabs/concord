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
import { useEffect, useRef, useState } from 'react';
import { Form } from 'semantic-ui-react';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { createOrUpdate as apiCreateOrUpdate, OutVariablesMode } from '../../../api/org/project';
import { RequestErrorActivity } from '../index';

export interface Props {
    orgName: ConcordKey;
    projectId: ConcordId;
    initialValue?: OutVariablesMode;
}

const getDescription = (m: OutVariablesMode): string => {
    switch (m) {
        case OutVariablesMode.DISABLED:
            return 'Sending custom out variable names is disabled. Only out variables defined in concord.yml can be used.';
        case OutVariablesMode.OWNERS:
            return "Only the project's owner and team members with OWNER privileges can specify custom out variable names.";
        case OutVariablesMode.TEAM_MEMBERS:
            return "Only the members of the teams assigned to the project's can specify custom out variable names.";
        case OutVariablesMode.ORG_MEMBERS:
            return "Only the project's organization members can specify custom out variable names.";
        case OutVariablesMode.EVERYONE:
            return 'Everyone can can specify custom out variable names. This is the least secure option.';
        default:
            return `Unknown raw payload mode: ${m}`;
    }
};

export default ({ orgName, projectId, initialValue = OutVariablesMode.DISABLED }: Props) => {
    const [value, setValue] = useState(initialValue);
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();

    const didMountRef = useRef(false);
    // TODO react-hooks/exhaustive-deps warning
    useEffect(() => {
        const update = async () => {
            if (!didMountRef.current) {
                didMountRef.current = true;
                return;
            }

            try {
                setUpdating(true);
                await apiCreateOrUpdate(orgName, {
                    id: projectId,
                    outVariablesMode: value
                });
            } catch (e) {
                setError(e);
            } finally {
                setUpdating(false);
            }
        };

        update();
    }, [orgName, projectId, value]); // eslint-disable-line react-hooks/exhaustive-deps

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <Form loading={updating}>
                <Form.Group inline={true}>
                    <Form.Dropdown
                        selection={true}
                        value={value}
                        onChange={(ev, data) => setValue(data.value as OutVariablesMode)}
                        options={[
                            { value: OutVariablesMode.DISABLED, text: 'Disabled', icon: 'lock' },
                            { value: OutVariablesMode.OWNERS, text: 'Only owners' },
                            { value: OutVariablesMode.TEAM_MEMBERS, text: 'Only team members' },
                            {
                                value: OutVariablesMode.ORG_MEMBERS,
                                text: 'Only organization members'
                            },
                            {
                                value: OutVariablesMode.EVERYONE,
                                text: 'Everyone',
                                icon: 'lock open'
                            }
                        ]}
                    />

                    <p>{getDescription(value)}</p>
                </Form.Group>
            </Form>
        </>
    );
};
