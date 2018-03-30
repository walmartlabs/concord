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
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';
import ErrorMessage from '../../shared/ErrorMessage';
import * as actions from './actions';
import * as selectors from './reducers';
import reducers from './reducers';
import sagas from './sagas';

class PortalPage extends Component {
    componentDidMount() {
        const { entryPoint, startFn } = this.props;
        startFn(entryPoint);
    }

    componentDidUpdate(prevProps) {
        const { entryPoint, startFn } = this.props;
        if (entryPoint !== prevProps.entryPoint) {
            startFn(entryPoint);
        }
    }

    render() {
        const { submitting, error } = this.props;

        if (error) {
            return <ErrorMessage message={error} />;
        }

        return (
            <div>
                <Loader active={submitting} />
            </div>
        );
    }
}

const mapStateToProps = ({ portal }, { location: { query } }) => ({
    entryPoint: query.entryPoint,
    error: selectors.getError(portal),
    submitting: selectors.getIsSubmitting(portal)
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (entryPoint) => dispatch(actions.startProcess(entryPoint))
});

export default connect(mapStateToProps, mapDispatchToProps)(PortalPage);

export { reducers, sagas };
