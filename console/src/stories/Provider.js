import React from 'react';
import {Provider as ReduxProvider} from 'react-redux';

import configureStore from "../store";
import reducers from "../reducers";
import {hashHistory} from "react-router";

const store = configureStore(hashHistory, reducers);

export default function Provider({story}) {
    return (
        <ReduxProvider store={store}>
            {story}
        </ReduxProvider>
    );
};
