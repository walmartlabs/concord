import React from 'react';
import { shallow, mount } from 'enzyme';
import CreatePlainSecret from './';

import { Form } from 'semantic-ui-react';

describe('CreatePlainSecret Component', () => {

    it('should render with no errors', () => {
        shallow(<CreatePlainSecret />);
    });

    it('should render a text input for the Secret Name', () => {
        const wrapper = shallow(<CreatePlainSecret />);
        expect( wrapper ).toMatchSnapshot();
    });

});