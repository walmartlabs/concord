import * as React from 'react';

import { storiesOf } from '@storybook/react';
import LocalTimestamp from './';

storiesOf('molecules/LocalTimestamp', module)
    .add('Good Date', () => <LocalTimestamp value={'2018-02-05 19:03:19'} />)
    .add('Not a date format', () => <LocalTimestamp value={'blahhhhhhhhh'} />)
    .add('No Value', () => <LocalTimestamp />);
