import React, { Component } from 'react';
// import { BrowsercRouter as Router, Route, NavLink, Link } from 'react-router-dom'
import { combineReducers } from 'redux';

import SecretCreate from './create'
import SecretList from './list';

export class Secret extends Component {
    render( props ) {
        return (
            <div>
                <SecretCreate />
                <SecretList />
            </div>
        );
    }
}
