import React from "react";
import ReactDOM from "react-dom";
import {Provider} from "react-redux";
import configureStore from "./configureStore";
import routes from "./routes";
import "./index.css";

const store = configureStore();

ReactDOM.render(
    <Provider store={store}>{routes(store)}</Provider>,
    document.getElementById("root")
);
