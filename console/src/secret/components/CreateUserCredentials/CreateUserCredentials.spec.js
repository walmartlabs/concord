import React from 'react';
import { shallow, mount } from 'enzyme';
import CreateUserCredentials from './';

import { Form } from 'semantic-ui-react';

describe('CreateUserCredentials Component', () => {

    it('should render with no errors', () => {
        shallow(<CreateUserCredentials />);
    });

    it('should render a text input for the Secret Name', () => {
        const wrapper = shallow(<CreateUserCredentials />);
        expect( wrapper ).toMatchSnapshot();
    });
    
});