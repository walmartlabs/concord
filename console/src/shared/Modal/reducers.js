// @flow
import types from "./actions";

const INITIAL = {kind: null, opts: {}};

export default (state: any = INITIAL, {type, kind, opts}: any) => {
    switch (type) {
        case types.MODAL_OPEN:
            return {kind, opts};
        case types.MODAL_CLOSE:
            return INITIAL;
        default:
            return state;
    }
};

