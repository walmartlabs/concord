import { combineReducers } from "redux";
import * as common from "../../reducers/common";
import types from "./actions";

const initialState = {
    power: {
        level: 100
    }
}

const test = (state=initialState, action) => {
    switch (action) {
        case types.USER_SECRET_CREATE_KEYPAIR:
            return Object.assign({}, state, {
                power: {
                    level: state.power.level + 1
                }
            });
        default:
            return state;
    }
};

export default combineReducers({ changesQuestions });

const mapStateToProps = ( state ) => ({
    power: {
        level: state.level
    }
});

const mapDispatchToProps = (dispatch) => ({
    doLogout: () => dispatch(actions.logOut())
});


