import React from 'react';
import ReactDOM from 'react-dom';
import { shallow } from 'enzyme';
import { Secret } from './';

describe('Secret Module', () => {
    it('should render without crashing', () => {
        shallow(<Secret />);
    })
})