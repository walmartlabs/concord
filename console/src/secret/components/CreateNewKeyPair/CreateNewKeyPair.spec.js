// @flow

import React from 'react';
import { shallow, mount } from 'enzyme';

import CreateNewKeyPair from './';

describe('CreateNewKeyPair', () => {

    it('should render without crashing', () => {
        shallow(<CreateNewKeyPair success={false} error={false} message={" "} />);
    });

    it('should match snapshots for various states of success and error', () => {
        const successStates = [ true, false ];
        const errorStates = [ true, false ];
        
        successStates.forEach( s => {
            errorStates.forEach( e => {
                let wrapper = shallow(<CreateNewKeyPair success={s} error={e} />)
                expect( wrapper ).toMatchSnapshot();
            });
        });
    });

})