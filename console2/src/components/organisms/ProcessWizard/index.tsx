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
import { Loader } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/forms';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    processInstanceId: ConcordId;
}

interface StateProps {
    error?: RequestError;
}

interface DispatchProps {
    start: (processInstanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessWizard extends React.PureComponent<Props> {
    componentDidMount() {
        const { start, processInstanceId } = this.props;
        start(processInstanceId);
    }

    render() {
        const { error } = this.props;
        if (error) {
            return <RequestErrorMessage error={error} />;
        }
        return <Loader active={true} />;
    }
}

const mapStateToProps = ({ forms }: { forms: State }): StateProps => ({
    error: forms.wizard.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    start: (processInstanceId: ConcordId) => dispatch(actions.startWizard(processInstanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessWizard);
