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
// @ts-nocheck

import * as React from 'react';
import { RightWrap } from './styles';
import CheckpointGroup from '../CheckpointGroup';
import NoCheckpointsMessage from '../NoCheckpointsMessage';

interface Props {
    process: any;
    checkpointGroups: any;
}

export default ({ process, checkpointGroups }: Props) => (
    <RightWrap>
        {process.checkpoints && (
            <>
                {process.checkpoints.length && (
                    <CheckpointGroup
                        processStatus={process.status}
                        processId={process.instanceId}
                        checkpointGroups={checkpointGroups[process.instanceId]}
                    />
                )}
            </>
        )}
        {!process.checkpoints && <NoCheckpointsMessage />}
    </RightWrap>
);
