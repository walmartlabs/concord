/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { storiesOf } from '@storybook/react';
import ProcessStatusIcon from './';
import { ProcessStatus } from '../../../api/process';

const status: ProcessStatus[] = [
    ProcessStatus.PREPARING,
    ProcessStatus.ENQUEUED,
    ProcessStatus.STARTING,
    ProcessStatus.RUNNING,
    ProcessStatus.SUSPENDED,
    ProcessStatus.RESUMING,
    ProcessStatus.FINISHED,
    ProcessStatus.FAILED
];

export type IconSizeProp = 'mini' | 'tiny' | 'small' | 'large' | 'big' | 'huge' | 'massive';

const sizes: IconSizeProp[] = ['mini', 'tiny', 'small', 'large', 'big', 'huge', 'massive'];

storiesOf('molecules/Process Status Icon', module).add('Default', () => (
    <>
        <h1>ProcessStatusIcons</h1>
        {status.map((value, i) => (
            <div key={i}>
                <h3>{value}</h3>
                {sizes.map((size, j) => (
                    <ProcessStatusIcon key={j} status={value} size={size} inverted={false} />
                ))}
            </div>
        ))}
    </>
));

storiesOf('molecules/Process Status Icon', module).add('Inverted', () => (
    <>
        <h1>ProcessStatusIcons</h1>
        {status.map((value, i) => (
            <div key={i}>
                <h3>{value}</h3>
                {sizes.map((size, j) => <ProcessStatusIcon key={j} status={value} size={size} />)}
            </div>
        ))}
    </>
));
