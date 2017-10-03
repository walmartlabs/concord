import React from 'react'
import { types, actions, reducers, sagas } from './create'

import { shallow, mount } from 'enzyme'

describe('Secret Create', () => {

    describe('reducers and actions', () => {
           
        const initialState = {
            error: false,
            success: false,
            publicKey: null,
            message: '',
            isLoading: false
        };

        it('default initial return should match initial state', () => {
            const resultState = reducers( undefined, { type: '' });
            expect( resultState ).toEqual( initialState );
        });

        it('should match snapshot for CreateSuccess Action', () => {
            const resultState = reducers( undefined, actions.createSuccess() );
            expect( resultState ).toMatchSnapshot();
        });

        it('should match snapshot for CreateFailed Action', () => {
            const resultState = reducers( undefined, actions.createFailed() );
            expect( resultState ).toMatchSnapshot();
        });

        it('should match snapshot for CreateNewKeyPair', () => {
            const resultState = reducers( undefined, actions.createNewKeyPair( "doop", false ) );
            expect ( resultState ).toMatchSnapshot();
        });

    })

})