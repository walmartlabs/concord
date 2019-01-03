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

import { CheckpointGroupName, CheckpointName } from '../shared/Labels';
import { ProcessStatus } from '../../../../api/process';
import { CheckpointGroup } from '../shared/types';

import {
    FlexWrapper,
    GroupWrapper,
    GroupItems,
    CheckpointNode,
    TimeBox,
    getStatusColor
} from './styles';
import CheckpointPopup from '../CheckpointPopup';
import { formatDuration, timestampDiffMs } from '../../../../utils';

interface Props {
    processId: string;
    processStatus: ProcessStatus;
    checkpointGroups: CheckpointGroup[];
}

export const CheckpointData: React.SFC<Props> = ({ processId, checkpointGroups }) => {
    if (checkpointGroups && checkpointGroups.length > 0) {
        return (
            <FlexWrapper>
                {checkpointGroups.map((group, indexA) => {
                    if (group.checkpoints.length > 0) {
                        return (
                            <GroupWrapper key={indexA}>
                                <CheckpointGroupName>Run {group.name}</CheckpointGroupName>
                                <GroupItems>
                                    {group.checkpoints.map((checkpoint, indexB) => (
                                        <CheckpointNode key={indexB}>
                                            <CheckpointName as="span">
                                                {checkpoint.name}
                                            </CheckpointName>
                                            <CheckpointPopup
                                                checkpoint={checkpoint}
                                                processId={processId}
                                                render={
                                                    <TimeBox
                                                        statusColor={getStatusColor(
                                                            checkpoint.status
                                                        )}>
                                                        {formatDuration(
                                                            timestampDiffMs(
                                                                checkpoint.endTime,
                                                                checkpoint.startTime
                                                            )
                                                        )}
                                                    </TimeBox>
                                                }
                                            />
                                        </CheckpointNode>
                                    ))}
                                </GroupItems>
                            </GroupWrapper>
                        );
                    } else {
                        return null;
                    }
                })}
            </FlexWrapper>
        );
    } else {
        return null;
    }
};

export default CheckpointData;
