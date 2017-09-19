import React, { Component } from 'react';
import { CreateNewKeyPair } from './forms';
import sagas from "./sagas";

export default class CreateSecret extends Component {

    constructor( props ) {
        super( props );
        this.state = {
            name: "NEW_SECRET"
        }
    }

    render() {
        return (
            <div> 
                <CreateNewKeyPair />
            </div> 
        )
    }
}

export { sagas }