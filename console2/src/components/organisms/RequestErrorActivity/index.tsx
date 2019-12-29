/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import * as React from 'react';
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Redirect } from 'react-router';

import { RequestError } from '../../../api/common';
import { actions } from '../../../state/session';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    error: RequestError;
}

interface DispatchProps {
    logOut: () => void;
}

type Props = DispatchProps & ExternalProps;

class RequestErrorActivity extends React.Component<Props> {
    render() {
        const { error, logOut } = this.props;

        if (error && error.status === 401) {
            logOut();
            return <Redirect to={'/unauthorized'} />;
        }

        return <RequestErrorMessage error={error} />;
    }
}

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    logOut: () => dispatch(actions.setCurrent({}))
});

export default connect(null, mapDispatchToProps)(RequestErrorActivity);
