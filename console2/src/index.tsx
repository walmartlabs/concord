import 'raf/polyfill';

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import 'lato-font/css/lato-font.min.css';
import 'semantic-ui-css/semantic.css';
import './index.css';

import App from './App';

ReactDOM.render(<App />, document.getElementById('root') as HTMLElement);
