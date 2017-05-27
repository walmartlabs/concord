import React from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {getFormValues} from "redux-form";
import {actions as modal} from "../../shared/Modal";
import RepositoryForm from "./form";
import * as c from "./constants";
import {actions as repoActions} from "../repository";

export const MODAL_TYPE = "NEW_REPOSITORY_POPUP";

const newRepositoryPopup = ({onSuccess, onCloseFn, ...rest}) => {
    const onSubmit = (data) => {
        const o = Object.assign({}, data);

        if (o.sourceType === c.REV_SOURCE_TYPE) {
            delete o.branch;
        } else {
            delete o.commitId;
        }

        delete o.sourceType;

        onCloseFn();
        onSuccess(o); // TODO replace with plain action objects
    };

    return <RepositoryForm onSubmit={onSubmit} onCloseFn={onCloseFn} {...rest}/>;
};

newRepositoryPopup.MODAL_TYPE = MODAL_TYPE;

newRepositoryPopup.propTypes = {
    onSuccess: PropTypes.func.isRequired
};

const mapStateToProps = (state) => ({
    data: getFormValues("repository")(state)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => {
        dispatch(modal.close());
        dispatch(repoActions.resetTest());
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(newRepositoryPopup);