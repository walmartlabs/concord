import React from "react";
import ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {hashHistory} from "react-router";
import configureStore from "./configureStore";
import routes from "./routes";
import "./index.css";

const history = hashHistory;
const store = configureStore(history);

ReactDOM.render(
    <Provider store={store}>{routes(store, history)}</Provider>,
    document.getElementById("root")
);
