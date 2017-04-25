import React from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import * as actions from "./actions";
import reducers from "./reducers";

const modal = ({types, kind, opts}) => {
    if (!kind) {
        return <div/>;
    }

    const Delegate = types[kind];
    if (!Delegate) {
        throw new Error(`Unknown modal type: ${kind}`);
    }

    return <Delegate open {...opts}/>;
};

modal.propTypes = {
    types: PropTypes.object.isRequired,
    kind: PropTypes.string,
    opts: PropTypes.object
};

const mapStateToProps = ({modal: {kind, opts}}) => ({
    kind,
    opts
});

export default connect(mapStateToProps)(modal);

export {actions, reducers};
