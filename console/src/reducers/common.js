export const makeListRowsReducer = (resultActionType) => (state = [], action) => {
    switch (action.type) {
        case resultActionType:
            if (action.error) {
                return state;
            }
            return action.response;
        default:
            return state;
    }
};

export const makeListLoadingReducer = (requestActionType, resultActionType) => (state = false, action) => {
    switch (action.type) {
        case requestActionType:
            return true;
        case resultActionType:
            return false;
        default:
            return state;
    }
};

export const makeListErrorReducer = (resultActionType) => (state = null, action) => {
    switch (action.type) {
        case resultActionType:
            if (action.error) {
                return action.message;
            }
            return null;
        default:
            return state;
    }
};

export const makeListLastQueryReducer = (resultActionType) => (state = null, action) => {
    switch (action.type) {
        case resultActionType:
            return { sortBy: action.sortBy, sortDir: action.sortDir };
        default:
            return state;
    }
};
