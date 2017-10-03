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

const success = ( state: boolean = false, action: Object ): boolean => {
    switch (action.type) {
        case types.CREATE_SUCCESS:
            return true;
        case types.CREATE_FAILED:
            return false;
        default:
            return state;
    }
}

const error = ( state: boolean = false, action: Object ): boolean => {
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
    console.log( " PUBLIC KEY REDUCER " + JSON.stringify(action) );
    switch (action.type) {
        case types.SAVE_PUBKEY:
            return action.publicKey || null;
        case types.CREATE_FAILED:
            return null;
        default:
            return state;
    }
}

const message = ( state: string = '', action: Object ): string => {
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
    console.log("REDUCER ISLOADING: " + JSON.stringify( action ) );
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
    CreateNewKeyPair: ( values ) => {
        return ({
            type: types.CREATE_KEYPAIR,
            payload: {
                name: values.ConcordId, 
                generatePassword: values.generatePassword
            }
        });
    },
    CreateWithExistingKeys: ( values ) => {
        return ({
            type: types.CREATE_WITH_EXISTING_KEYPAIR,
            payload: {
                name: values.ConcordId
                // generatePassword: values.generatePassword, << Should we suppor this?
            }
        });
    },
    CreateWithUserCredentials: ( values ) => {
        return ({
            type: types.CREATE_WITH_USER_CREDENTIALS,
            payload: {
                name: values.ConcordId,
                generatePassword: values.generatePassword
            }
        });
    },
    CreatePlainSecret: ( values ) => {
        console.log("Action Creator" + JSON.stringify( values ) );
        return ({
            type: types.CREATE_PLAIN,
            payload: {
                name: values.ConcordId,
                secret: values.secret
            }
        });
    },
    CreateSuccess: ( message: string = "CREATE SUCCESS" ) => {
        return ({
            type: types.CREATE_SUCCESS,
            message
        });
    },
    CreateFailed: ( message: string = "CREATE FAILED" ) => {
        return ({
            type: types.CREATE_FAILED,
            message,
            error: error.message
        });
    },
    SavePublicKey: ( publicKey ) => {
        return ({
            type: types.SAVE_PUBKEY,
            publicKey
        });
    },
    StartLoading: () => {
        return ({
            type: types.START_LOADING
        });
    }
}

// Side Effects: Sagas

export function* createNewKeyPair(action: any): Generator<*, *, *> {
    try {
        yield put( actions.StartLoading() );
        const response = yield call(api.createNewKeyPair, action.payload.name, action.payload.generatePassword, action.payload.storePassword);        
        yield put( actions.SavePublicKey( response.publicKey ));
        yield put( actions.CreateSuccess("Successfully Generated a New Secret Key Pair!  Save your public Key!") );
    } catch (e) {
        yield put( actions.CreateFailed( "Failed to create a new KeyPair.  Name may already be taken or Data may be malformed." ) );
    }
}

export function* createPlainSecret(action: any): Generator<*, *, *> {
    try {
        console.log( action.payload, JSON.stringify( action ) ); 

        const response = yield call(api.createPlainSecret, 
            action.payload.name, 
            action.payload.generatePassword, 
            action.payload.secret );
        
        console.log("API RESPONSE " + JSON.stringify(response) );

        yield put({
            type: types.CREATE_SUCCESS,
            response
        });

    } catch (e) {

        yield put({
            type: types.CREATE_FAILED,
            error: true,
            message: e.message || "Error while creating new Plain Secret"
        });

    }
}

export function* uploadExistingKey(action: any): Generator<*, *, *> {
    try {
        
        console.log( action.payload, JSON.stringify(action))

        const response = yield call(api.uploadExistingKeyPair, 
            action.payload.name, 
            action.payload.generatePassword, 
            action.payload.publicKey,
            action.payload.privateKey,
            action.payload.storePassword );
        
        console.debug(response)

        yield put({
            type: types.CREATE_SUCCESS,
            response
        });

    } catch (e) {

        yield put({
            type: types.CREATE_FAILED,
            error: true,
            message: e.message || "Error while creating new Plain Secret"
        });

    }
}

export function* createUserCredentials(action: any): Generator<*, *, *> {
    try {
        console.log( action.payload, JSON.stringify(action))
        
        const response = yield call(api.uploadExistingKeyPair, 
            action.payload.name, 
            action.payload.generatePassword, 
            action.payload.username,
            action.payload.password,
            action.payload.storePassword );
        
        console.debug(response)

        yield put({
            type: types.CREATE_SUCCESS,
            response
        });

    } catch (e) {

        yield put({
            type: types.CREATE_FAILED,
            error: true,
            message: e.message || "Error while creating new Plain Secret"
        });

    }
}

// export function* uploadExistingKeyPair( action: any ): Generator<*, *, *> {
//     try {
//         const response = yeild call()
//     } catch ( error ) {
//         yield put({
//             type: types.CREATE_WITH_EXISTING_KEYPAIR_RESPONSE,
//             error: true,
//             message: error.message || "Error while uploading your keypair"
//         })
//     }
// }

export const sagas = function*(): Generator<*, *, *> {

    yield [
        fork(takeLatest, types.CREATE_KEYPAIR, createNewKeyPair),
        fork(takeLatest, types.CREATE_PLAIN, createPlainSecret),
        fork(takeLatest, types.CREATE_WITH_EXISTING_KEYPAIR, uploadExistingKey),
        fork(takeLatest, types.CREATE_WITH_USER_CREDENTIALS, createUserCredentials)
    ];

}
