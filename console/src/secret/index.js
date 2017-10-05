import React, { Component } from 'react';
// import { BrowsercRouter as Router, Route, NavLink, Link } from 'react-router-dom'
import { combineReducers } from 'redux';

import SecretCreate from './create'
import SecretList from './list';

// TODO: Currently not used
// Idea here is that this module index component could control the routing, layout,
// and display of it's containers and components separately from the main routes,
// 

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
