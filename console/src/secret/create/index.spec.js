import React from 'react';
import { shallow } from 'enzyme';
import { SecretCreate } from './';

describe('Secret Create Container', () => {
        
    it('should match snapshot', () => {
        const wrapper = shallow(<SecretCreate />);
        expect( wrapper ).toMatchSnapshot();
    })

    it('should render a semantic-ui-react Container', () => {
        const wrapper = shallow(<SecretCreate />);
        expect(wrapper.find('Container').length).toEqual(1);
    });

})