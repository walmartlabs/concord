import React from 'react';

import {storiesOf} from '@storybook/react';
import {action} from '@storybook/addon-actions';
import {linkTo} from '@storybook/addon-links';

import {Button, Welcome} from '@storybook/react/demo';
// Workaround: Storybook otherwise doesn't know how to style semantic-ui components
import 'semantic-ui-css/semantic.min.css';
// Mock Provider
import Provider from './Provider';
// Concord Components
import {CreateNewKeyPair, CreatePlainSecret, CreateUserCredentials, UploadExistingKeys} from "../secret/components";

storiesOf('Welcome', module).add('to Storybook', () => <Welcome showApp={linkTo('Button')}/>);

storiesOf('Button', module)
    .add('with text', () => <Button onClick={action('clicked')}>Hello Button</Button>)
    .add('with some emoji', () => <Button onClick={action('clicked')}>😀 😎 👍 💯</Button>);

storiesOf('Secrets', module)
    .addDecorator(story => <Provider story={story()}/>)
    .add("Create New Keypair", () => <CreateNewKeyPair onSubmit={action('Mock form submit!')}/>)
    .add("Upload Existing Keypair", () => <UploadExistingKeys onSubmit={action('Mock form submit!')}/>)
    .add("Create New Plain Secret", () => <CreatePlainSecret onSubmit={action('Mock form submit!')}/>)
    .add("Create User Credentials", () => <CreateUserCredentials onSubmit={action('Mock form submit!')}/>);
