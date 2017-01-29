import {createStore, applyMiddleware, combineReducers} from "redux";
import createLogger from "redux-logger";
import {routerReducer} from "react-router-redux";
import {reducer as formReducer} from "redux-form";
import createSagaMiddleware from "redux-saga";
import consoleApp from "./reducers";
import saga from "./sagas";

const configureStore = () => {
    const sagaMiddleware = createSagaMiddleware();

    const middleware = [sagaMiddleware];
    if (process.env.NODE_ENV !== "production") {
        middleware.push(createLogger());
    }

    const reducers = combineReducers({
        ...consoleApp,
        routing: routerReducer,
        form: formReducer
    });

    const store = createStore(reducers, applyMiddleware(...middleware));

    sagaMiddleware.run(saga);

    return store;
};

export default configureStore;