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
import { ProcessCheckpointEntry, ProcessHistoryEntry, ProcessStatus } from '../../../api/process';
import { ContentBlock } from '../CheckpointView/ProcessList/styles';
import { generateCheckpointGroups } from '../CheckpointView/Container/checkpointUtils';
import {
    CheckpointNode,
    EmptyBox,
    FlexWrapper,
    getStatusButtonColor,
    GroupItems,
    GroupWrapper
} from '../CheckpointView/CheckpointGroup/styles';
import { CheckpointGroupName } from '../CheckpointView/shared/Labels';
import { format as formatDate } from 'date-fns';
import { SingleOperationPopup } from '../../molecules';
import { isFinalStatus } from '../ProcessRestoreActivity';
import { Button, Popup } from 'semantic-ui-react';
import { restoreProcess as apiRestore } from '../../../api/process/checkpoint';
import { useCallback, useState } from 'react';
import { ConcordId, RequestError } from '../../../api/common';

interface ExternalProps {
    instanceId: ConcordId;
    processStatus: ProcessStatus;
    processDisabled: boolean;
    checkpoints: ProcessCheckpointEntry[];
    statusHistory: ProcessHistoryEntry[];
    onRestoreComplete: () => void;
}

interface Props {
    instanceId: ConcordId;
    processStatus: ProcessStatus;
    processDisabled: boolean;
    checkpointId: string;
    checkpointStatus: ProcessStatus;
    checkpointName: string;
    checkpointStartTime: Date;
    onRestoreComplete: () => void;
}

const Checkpoint = (props: Props) => {
    const [restoring, setRestoring] = useState(false);
    const [success, setSuccess] = useState(false);
    const [restoringError, setRestoringError] = useState<RequestError>();

    const { onRestoreComplete, instanceId, checkpointId } = props;

    const restoreProcess = useCallback(async () => {
        setRestoring(true);

        try {
            await apiRestore(instanceId, checkpointId);
            setRestoringError(undefined);
            setSuccess(true);

            onRestoreComplete();
        } catch (e) {
            setRestoringError(e);
        } finally {
            setRestoring(false);
        }
    }, [onRestoreComplete, instanceId, checkpointId]);

    const reset = useCallback(() => {
        setRestoring(false);
        setSuccess(false);
        setRestoringError(undefined);
    }, []);

    const {
        processStatus,
        checkpointName,
        checkpointStartTime,
        checkpointStatus,
        processDisabled
    } = props;

    return (
        <SingleOperationPopup
            trigger={(onClick: any) => (
                <Popup
                    content={
                        checkpointStartTime.toDateString() +
                        ' ' +
                        formatDate(checkpointStartTime, 'HH:mm:ss')
                    }
                    trigger={
                        <span>
                            <Button
                                onClick={onClick}
                                disabled={!isFinalStatus(processStatus) || processDisabled}
                                color={getStatusButtonColor(checkpointStatus)}
                                style={{
                                    margin: '0',
                                    padding: '0.7rem 0.7rem',
                                    fontSize: '1rem',
                                    fontWeight: '900'
                                }}>
                                {checkpointName}
                            </Button>
                        </span>
                    }
                />
            )}
            title="Restore the process?"
            introMsg={
                <p>
                    Are you sure you want to restore process at '<b>{checkpointName}</b>'
                    checkpoint?
                </p>
            }
            running={restoring}
            success={success}
            successMsg={<p>Process restored successfully.</p>}
            error={restoringError}
            reset={reset}
            onConfirm={restoreProcess}
        />
    );
};

const ProcessCheckpointActivity = (props: ExternalProps) => {
    const checkpointGroups = generateCheckpointGroups(props.checkpoints, props.statusHistory);

    return (
        <ContentBlock style={{ margin: '16px 0' }}>
            <FlexWrapper>
                {checkpointGroups.map(({ name, checkpoints }, indexA) => (
                    <GroupWrapper key={indexA}>
                        <CheckpointGroupName>Run {name}</CheckpointGroupName>
                        <GroupItems>
                            {checkpoints.length === 0 && (
                                <div>
                                    <EmptyBox>No checkpoints</EmptyBox>
                                </div>
                            )}
                            {checkpoints.length > 0 &&
                                checkpoints.map((checkpoint, indexB) => (
                                    <CheckpointNode key={indexB}>
                                        <Checkpoint
                                            instanceId={props.instanceId}
                                            processStatus={props.processStatus}
                                            processDisabled={props.processDisabled}
                                            checkpointId={checkpoint.id}
                                            checkpointName={checkpoint.name}
                                            checkpointStatus={checkpoint.status}
                                            checkpointStartTime={checkpoint.startTime}
                                            onRestoreComplete={props.onRestoreComplete}
                                        />
                                    </CheckpointNode>
                                ))}
                        </GroupItems>
                    </GroupWrapper>
                ))}
            </FlexWrapper>
        </ContentBlock>
    );
};

export default ProcessCheckpointActivity;
