import React, {Component} from "react";
import {connect} from "react-redux";
import {withRouter} from "react-router";
import Layout from "../../components/Layout";
import {getIsLoggedIn} from "../../reducers";

class VisibleLayout extends Component {

    render() {
        return <Layout {...this.props}/>
    }
}

const mapStateToProps = (state) => ({
    loggedIn: getIsLoggedIn(state)
});

export default withRouter(connect(mapStateToProps)(VisibleLayout));
