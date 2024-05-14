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

import { ConcordId, RequestError } from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';
import { memo, useCallback } from 'react';
import { restart as apiRestart } from '../../../api/process';
import { useState } from 'react';
import { Message } from "semantic-ui-react";

interface ExternalProps {
    instanceId: ConcordId;
    rootInstanceId?: ConcordId;
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

const RestartProcessPopup = memo((props: ExternalProps) => {
    const [restarting, setRestarting] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [success, setSuccess] = useState(false);

    const instanceId = props.instanceId;
    const rootInstanceId = props.rootInstanceId;

    const restartProcess = useCallback(async () => {
        setRestarting(true);

        try {
            await apiRestart(instanceId);
            setSuccess(true);
        } catch (e) {
            setError(e);
        } finally {
            setRestarting(false);
        }
    }, [instanceId]);

    const reset = useCallback(() => {
        setRestarting(false);
        setSuccess(false);
        setError(undefined);
    }, []);

    const { trigger, refresh } = props;

    return (
        <SingleOperationPopup
            trigger={trigger}
            title="Restart the process?"
            introMsg={
                <>
                    {!rootInstanceId && <p>Are you sure you want to restart the selected process?</p>}
                    {rootInstanceId &&
                        <>
                        <Message warning>
                            <Message.Header>You are about to restart a child process. Please note:</Message.Header>
                            <Message.List style={{marginTop: "10px"}}>
                                <Message.Item>
                                    Only the <a href={`#/process/${rootInstanceId}/log`} target="_blank"
                                                   rel="noopener noreferrer">parent (root) process</a> will be restarted.
                                </Message.Item>
                                <Message.Item>
                                    Restarting the parent process may also re-run this and other child processes.
                                </Message.Item>
                                <Message.Item>
                                    Ensure that this is the desired action before proceeding.
                                </Message.Item>
                            </Message.List>
                        </Message>

                        <p>Do you want to continue with restarting the parent process?</p>
                        </>
                    }
                </>
            }
            running={restarting}
            runningMsg={<p>Restarting...</p>}
            success={success}
            successMsg={<p>The restart command was sent successfully.</p>}
            error={error}
            reset={reset}
            onDone={refresh}
            onConfirm={restartProcess}
        />
    );
});

export default RestartProcessPopup;
