// @flow

export const booleanTrigger = (requestActionType: string, resultActionType: string) => (state: boolean = false, action: any) => {
    switch (action.type) {
        case requestActionType:
            return true;
        case resultActionType:
            return false;
        default:
            return state;
    }
};

export const error = (type: string) => (state: ?string = null, action: any) => {
    switch (action.type) {
        case type:
            if (action.error) {
                return action.message;
            }
            return null;
        default:
            return state;
    }
};
