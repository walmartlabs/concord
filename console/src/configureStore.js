import {createStore, applyMiddleware, combineReducers} from "redux";
import createLogger from "redux-logger";
import {routerReducer, routerMiddleware} from "react-router-redux";
import {reducer as formReducer} from "redux-form";
import createSagaMiddleware from "redux-saga";
import consoleApp from "./reducers";
import saga from "./sagas";

const configureStore = (history) => {
    const sagaMw = createSagaMiddleware();
    const routerMw = routerMiddleware(history);

    const middleware = [sagaMw, routerMw];
    if (process.env.NODE_ENV !== "production") {
        middleware.push(createLogger());
    }

    const reducers = combineReducers({
        ...consoleApp,
        routing: routerReducer,
        form: formReducer
    });

    const store = createStore(reducers, applyMiddleware(...middleware));

    sagaMw.run(saga);

    return store;
};

export default configureStore;