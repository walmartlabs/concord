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

import { ConcordId, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/processes';
import { SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    data: ConcordId[];
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
}

interface StateProps {
    cancelling: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class BulkCancelProcessPopup extends React.Component<Props> {
    render() {
        const { trigger, cancelling, success, error, reset, refresh, onConfirm } = this.props;

        return (
            <SingleOperationPopup
                trigger={trigger}
                title="Cancel the process(es)?"
                introMsg={<p>Are you sure you want to cancel the selected process(es)?</p>}
                running={cancelling}
                runningMsg={<p>Cancelling...</p>}
                success={success}
                successMsg={<p>The cancel command was sent successfully.</p>}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
                onDone={refresh}
            />
        );
    }
}

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    cancelling: processes.cancelBulkProcess.running,
    success: !!processes.cancelBulkProcess.response,
    error: processes.cancelBulkProcess.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { data }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.resetBulk()),
    onConfirm: () => dispatch(actions.cancelBulk(data))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(BulkCancelProcessPopup);
