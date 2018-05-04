import * as React from 'react';
import { storiesOf } from '@storybook/react';
import { action } from '@storybook/addon-actions';
// import StoryRouter from 'storybook-react-router';

import GlobalNavMenu from './';

storiesOf('molecules/GlobalNavMenu', module)
    // .addDecorator(StoryRouter())
    // TODO: Wrap Component in a Router context
    .add('Default', () => (
        <>
            <GlobalNavMenu
                activeTab="process"
                userDisplayName={'Test Dummy'}
                logOut={() => action('Clicked Logout')}
            />
        </>
    ));
