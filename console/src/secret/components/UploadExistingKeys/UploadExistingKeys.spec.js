import React from 'react';
import { shallow, mount } from 'enzyme';
import UploadExistingKeys from './';

import { Form } from 'semantic-ui-react';

describe('UploadExistingKeys Component', () => {

    it('should render with no errors', () => {
        shallow(<UploadExistingKeys />);
    });

    it('should render a text input for the Secret Name', () => {
        const wrapper = shallow(<UploadExistingKeys />);
        expect( wrapper ).toMatchSnapshot();
    });
    
});