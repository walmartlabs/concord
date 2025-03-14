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
import { createOrUpdate as apiCreateOrUpdate, RawPayloadMode } from '../../../api/org/project';
import { RequestErrorActivity } from '../index';

export interface Props {
    orgName: ConcordKey;
    projectId: ConcordId;
    initialValue?: RawPayloadMode;
}

const getDescription = (m: RawPayloadMode): string => {
    switch (m) {
        case RawPayloadMode.DISABLED:
            return 'Sending payload archives (ZIP files or individual workflow files) is disabled. Only the configured repositories can be used to start a new process.';
        case RawPayloadMode.OWNERS:
            return "Only the project's owner and team members with OWNER privileges can send payload archives.";
        case RawPayloadMode.TEAM_MEMBERS:
            return "Only the members of the teams assigned to the project's can send payload archives.";
        case RawPayloadMode.ORG_MEMBERS:
            return "Only the project's organization members can send payload archives.";
        case RawPayloadMode.EVERYONE:
            return 'Everyone can send payload archives. This is the least secure option.';
        default:
            return `Unknown raw payload mode: ${m}`;
    }
};

export default ({ orgName, projectId, initialValue = RawPayloadMode.DISABLED }: Props) => {
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
                    rawPayloadMode: value
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
                        onChange={(ev, data) => setValue(data.value as RawPayloadMode)}
                        options={[
                            { value: RawPayloadMode.DISABLED, text: 'Disabled', icon: 'lock' },
                            { value: RawPayloadMode.OWNERS, text: 'Only owners' },
                            { value: RawPayloadMode.TEAM_MEMBERS, text: 'Only team members' },
                            {
                                value: RawPayloadMode.ORG_MEMBERS,
                                text: 'Only organization members'
                            },
                            { value: RawPayloadMode.EVERYONE, text: 'Everyone', icon: 'lock open' }
                        ]}
                    />

                    <p>{getDescription(value)}</p>
                </Form.Group>
            </Form>
        </>
    );
};
