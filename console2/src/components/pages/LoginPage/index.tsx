import * as React from 'react';
import { Login } from '../../organisms';

import './styles.css';

export default class extends React.PureComponent {
    render() {
        return (
            <div className="flexbox-container">
                <Login />
            </div>
        );
    }
}
