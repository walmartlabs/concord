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
import { generateCheckpointGroups } from '../checkpointUtils';
import {
    emptyProcessCheckpoints,
    emptyProcessHistory,
    validProcessCheckpoints,
    validProcessHistory
} from '../__mocks__/checkpointUtils.mocks';
import { ProcessStatus } from '../../../../../api/process';

test('generateCheckpointGroups handles valid data', () => {
    const result = generateCheckpointGroups(
        ProcessStatus.FINISHED,
        validProcessCheckpoints,
        validProcessHistory
    );
    expect(result).toMatchSnapshot();
});

test('generateCheckpointGroups handles no data', () => {
    const result = generateCheckpointGroups(
        ProcessStatus.FINISHED,
        emptyProcessCheckpoints,
        emptyProcessHistory
    );
    expect(result).toEqual([]);
});

test('generateCheckpointGroups handles empty checkpoints', () => {
    const result = generateCheckpointGroups(
        ProcessStatus.FINISHED,
        emptyProcessCheckpoints,
        validProcessHistory
    );
    expect(result).toMatchSnapshot();
});

test('generateCheckpointGroups handles empty history', () => {
    const result = generateCheckpointGroups(
        ProcessStatus.FINISHED,
        validProcessCheckpoints,
        emptyProcessHistory
    );
    expect(result).toMatchSnapshot();
});
