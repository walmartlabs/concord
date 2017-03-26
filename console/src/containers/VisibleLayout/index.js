import React, {Component} from "react";
import {connect} from "react-redux";
import {withRouter} from "react-router";
import Layout from "../../components/Layout";
import {getIsLoggedIn} from "../../reducers";
import * as constants from "../../constants";

class VisibleLayout extends Component {

    render() {
        return <Layout {...this.props}/>
    }
}

const mapStateToProps = (state, {location: {query}}) => ({
    fullScreen: query.fullScreen === "true" || !getIsLoggedIn(state),
    title: query.title || constants.title
});

export default withRouter(connect(mapStateToProps)(VisibleLayout));
