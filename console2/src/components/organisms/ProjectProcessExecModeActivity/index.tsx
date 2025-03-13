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
import { createOrUpdate as apiCreateOrUpdate, ProcessExecMode } from '../../../api/org/project';
import { RequestErrorActivity } from '../index';

export interface Props {
    orgName: ConcordKey;
    projectId: ConcordId;
    initialValue?: ProcessExecMode;
}

const getDescription = (m: ProcessExecMode): string => {
    switch (m) {
        case ProcessExecMode.DISABLED:
            return 'No new processes are allowed to run within the context of the project.';
        case ProcessExecMode.READERS:
            return "READER (or above) privileges are necessary to execute a process. All users have READER access to public projects. Private projects must assign permissions explicitly. This is the default mode.";
        case ProcessExecMode.WRITERS:
            return "WRITER privileges are necessary to execute a process.";
        default:
            return `Unknown process exec mode: ${m}`;
    }
};

export default ({ orgName, projectId, initialValue = ProcessExecMode.DISABLED }: Props) => {
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
                    processExecMode: value
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
                        onChange={(ev, data) => setValue(data.value as ProcessExecMode)}
                        options={[
                            { value: ProcessExecMode.DISABLED, text: 'Disabled', icon: 'lock' },
                            { value: ProcessExecMode.READERS, text: 'READERs or WRITERs' },
                            { value: ProcessExecMode.WRITERS, text: 'WRITERs only' },
                        ]}
                    />

                    <p>{getDescription(value)}</p>
                </Form.Group>
            </Form>
        </>
    );
};
