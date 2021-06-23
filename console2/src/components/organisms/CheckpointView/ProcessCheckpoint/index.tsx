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

import { ContentBlock } from '../ProcessList/styles';
import CheckpointGroup from '../CheckpointGroup';
import { generateCheckpointGroups } from '../Container/checkpointUtils';
import { ProcessEntry } from '../../../../api/process';
import NoCheckpointsMessage from '../NoCheckpointsMessage';
import CheckpointErrorBoundary from '../CheckpointErrorBoundry';

interface Props {
    process: ProcessEntry;
}

export const ProcessCheckpoint: React.SFC<Props> = ({ process }) => {
    if (process.checkpoints) {
        return (
            <ContentBlock style={{ margin: '16px 0' }}>
                <CheckpointErrorBoundary>
                    <CheckpointGroup
                        process={process}
                        checkpointGroups={generateCheckpointGroups(
                            process.status,
                            process.checkpoints,
                            process.checkpointRestoreHistory
                        )}
                    />
                </CheckpointErrorBoundary>
            </ContentBlock>
        );
    } else {
        return (
            <ContentBlock style={{ margin: '16px 0' }}>
                <NoCheckpointsMessage />
            </ContentBlock>
        );
    }
};

export default ProcessCheckpoint;
