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
