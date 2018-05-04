// Import Semantic UI css globally for all project stories
import 'semantic-ui-css/semantic.min.css';

import * as React from 'react';

import { storiesOf } from '@storybook/react';

storiesOf('Default', module).add('Welcome', () => (
    <a href="https://storybook.js.org/">Main Storybook Website</a>
));
