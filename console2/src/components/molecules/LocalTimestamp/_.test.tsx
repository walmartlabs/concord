import 'raf/polyfill';

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import App from './';

describe('timestamp', () => {
    it('renders with good data', () => {
        const div = document.createElement('div');
        ReactDOM.render(<App value={'2018-02-05 19:03:19'} />, div);
    });

    it('renders with bad data', () => {
        const div = document.createElement('div');
        ReactDOM.render(<App value={' '} />, div);
    });
});
