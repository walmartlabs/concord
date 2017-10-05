import { combineReducers } from "redux";
import {call, fork, put, takeLatest} from "redux-saga/effects";
import * as api from "./api";

const NAMESPACE = "user/secret";

// Actions

export const types = {
    CREATE_KEYPAIR: `${NAMESPACE}/create/keypair`,
    CREATE_WITH_EXISTING_KEYPAIR: `${NAMESPACE}/create/existingkeypair`,
    CREATE_WITH_USER_CREDENTIALS: `${NAMESPACE}/create/usercredentials`,
    CREATE_PLAIN: `${NAMESPACE}/create/simple`,

    SAVE_PUBKEY: `${NAMESPACE}/create/savepublickey`,
    START_LOADING: `${NAMESPACE}/keypair/loading`,
    CREATE_FAILED: `${NAMESPACE}/keypair/fail`,
    CREATE_SUCCESS: `${NAMESPACE}/keypair/success`
};

// Reducers 

const success = ( state: boolean = false, action ): boolean => {
    switch (action.type) {
        case types.CREATE_SUCCESS:
            return true;
        case types.CREATE_FAILED:
            return false;
        default:
            return state;
    }
}

const error = ( state: boolean = false, action ): boolean => {
    switch (action.type) {
        case types.CREATE_SUCCESS:
            return false;
        case types.CREATE_FAILED:
            return true;
        default:
            return state;
    }
}

const publicKey = ( state = null, action ) => {
    switch (action.type) {
        case types.SAVE_PUBKEY:
            return action.publicKey || null;
        case types.CREATE_PLAIN:
        case types.CREATE_WITH_USER_CREDENTIALS:
        case types.CREATE_WITH_EXISTING_KEYPAIR:
        case types.CREATE_FAILED:
            return null;
        default:
            return state;
    }
}

const message = ( state: string = '', action ): string => {
    switch (action.type) {
        case types.CREATE_SUCCESS:
            return action.message || "CREATE SUCCESS";
        case types.CREATE_FAILED:
            return action.message || "CREATE FAILED";
        default:
            return state;
    }
}

const isLoading = ( state = false, action ) => {
    switch (action.type) {
        case types.START_LOADING:
            return true
        case types.CREATE_SUCCESS:
            return false;
        case types.CREATE_FAILED:
            return false;
        default:
            return state;
    }
}

export const reducers = combineReducers({ error, isLoading, message, publicKey, success });

// Action Creators

export const actions = {
    createNewKeyPair: ( values ) => {
        return ({
            type: types.CREATE_KEYPAIR,
            payload: {
                name: values.ConcordId, 
                generatePassword: values.generatePassword
                // storePassword: values.storePassword
            }
        });
    },
    createWithExistingKeys: ( values ) => {
        return ({
            type: types.CREATE_WITH_EXISTING_KEYPAIR,
            payload: {
                name: values.ConcordId,
                publicKey: values.publicKey,
                privateKey: values.privateKey
                // generatePassword: values.generatePassword
                // storePassword: values.storePassword
            }
        });
    },
    createWithUserCredentials: ( values ) => {
        return ({
            type: types.CREATE_WITH_USER_CREDENTIALS,
            payload: {
                name: values.ConcordId,
                generatePassword: values.generatePassword,
                username: values.username,
                password: values.password
                // storePassword: values.storePassword
            }
        });
    },
    createPlainSecret: ( values ) => {
        return ({
            type: types.CREATE_PLAIN,
            payload: {
                name: values.ConcordId,
                secret: values.secret,
                generatePassword: values.generatePassword
                // storePassword: values.storePassword
            }
        });
    },
    createSuccess: ( message = "CREATE SUCCESS" ) => {
        return ({
            type: types.CREATE_SUCCESS,
            message
        });
    },
    createFailed: ( message = "CREATE FAILED" ) => {
        return ({
            type: types.CREATE_FAILED,
            message
        });
    },
    savePublicKey: ( publicKey ) => {
        return ({
            type: types.SAVE_PUBKEY,
            publicKey
        });
    },
    startLoading: () => {
        return ({
            type: types.START_LOADING
        });
    }
}

// Side Effects: Sagas

export function* createNewKeyPair( action ) {
    try {

        yield put( actions.startLoading() );

        const response = yield call( api.createNewKeyPair, action.payload );

        yield put( actions.savePublicKey( response.publicKey ) );

        yield put( actions.createSuccess("Successfully Generated a New Secret Key Pair!  Save your public Key!") );

    } catch (e) {

        yield put( actions.createFailed( "Issues encountered while creating a new KeyPair.  Name may already be taken or Data may be malformed." ) );
    
    }
}

export function* createPlainSecret( action ) {
    try {

        yield put( actions.startLoading() );

        const response = yield call( api.createPlainSecret, action.payload );

        yield put( actions.createSuccess( `Successfully Created Plain Secret: ${JSON.stringify(response)}` ) );

    } catch (e) {

        yield put( actions.createFailed( `Issues Encountered while creating new Plain Secret: ${e.message}` ) );

    }
}

export function* uploadExistingKey( action ) {
    try {

        yield put( actions.startLoading() );

        const response = yield call( api.uploadExistingKeyPair, action.payload );

        yield put( actions.createSuccess( `Successfully Uploaded Existing Keypair! ${JSON.stringify(response) || ''}` ) );

    } catch (e) {

        yield put( actions.createFailed( `Issues encountered while uploading your Keypair: ${e.message}` ) );

    }
}

export function* createUserCredentials( action ) {
    try {

        yield put( actions.startLoading() );

        const response = yield call( api.createUserCredentials, action.payload );

        yield put( actions.createSuccess( `Successfully created User Credentials Secret! ${JSON.stringify(response) || ''}` ) );

    } catch (e) {

        yield put( actions.createFailed( `Issues encountered while creating new User Credentials ${e.message}` ) );
    
    }
}

export const sagas = function*() {

    yield [
        fork(takeLatest, types.CREATE_KEYPAIR, createNewKeyPair),
        fork(takeLatest, types.CREATE_PLAIN, createPlainSecret),
        fork(takeLatest, types.CREATE_WITH_EXISTING_KEYPAIR, uploadExistingKey),
        fork(takeLatest, types.CREATE_WITH_USER_CREDENTIALS, createUserCredentials)
    ];

}
