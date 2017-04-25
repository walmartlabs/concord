// @flow

import {applyMiddleware, combineReducers, createStore} from "redux";
import {createLogger} from "redux-logger";
import {routerMiddleware, routerReducer} from "react-router-redux";
import createSagaMiddleware from "redux-saga";
import sagas from "./sagas";

export default (history: any, reducers: any) => {
    const sagaMw = createSagaMiddleware();
    const routerMw = routerMiddleware(history);

    const middleware = [sagaMw, routerMw];
    if (process.env.NODE_ENV !== "production") {
        middleware.push(createLogger());
    }

    const combined = combineReducers({
        ...reducers,
        routing: routerReducer,
    });

    const store = createStore(combined, applyMiddleware(...middleware));

    sagaMw.run(sagas);

    return store;
}
