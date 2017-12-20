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
