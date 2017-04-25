import {connect as reduxConnect} from "react-redux";

// TODO what if a component needs several selectors?
export default (selector, component) => {
    let {mapStateToProps, mapDispatchToProps} = component;

    if (mapStateToProps) {
        let tmp = mapStateToProps;
        mapStateToProps = (state, ownProps) => tmp(selector(state), ownProps);
    }

    return reduxConnect(mapStateToProps, mapDispatchToProps)(component);
};
