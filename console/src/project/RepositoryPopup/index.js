/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
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
