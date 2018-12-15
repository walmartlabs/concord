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
import { connect, Dispatch } from 'react-redux';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/apiTokens';
import { SingleOperationPopup, RequestErrorMessage } from '../../molecules';
import { Button } from 'semantic-ui-react';

interface ExternalProps {
    id: ConcordId;
    name: ConcordKey;
}

interface StateProps {
    deleting: boolean;
    success: boolean;
    error: RequestError;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: (id: ConcordId) => void;
    onDone: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class APITokenDeleteActivity extends React.PureComponent<Props> {
    render() {
        const { success, error, reset, deleting, onConfirm, onDone, id, name } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}

                <SingleOperationPopup
                    trigger={(onClick) => (
                        <Button negative={true} icon="delete" content="Delete" onClick={onClick} />
                    )}
                    title="Delete API Token?"
                    introMsg={
                        <p>
                            Are you sure you want to delete the <b>{name}</b> API token?
                        </p>
                    }
                    running={deleting}
                    runningMsg={<p>Removing the API Token...</p>}
                    success={success}
                    successMsg={<p>The API Token was removed successfully.</p>}
                    error={error}
                    reset={reset}
                    onConfirm={() => onConfirm(id)}
                    onDone={onDone}
                />
            </>
        );
    }
}

const mapStateToProps = ({ tokens }: { tokens: State }): StateProps => ({
    deleting: tokens.deleteToken.running,
    success: !!tokens.deleteToken.response && tokens.deleteToken.response.ok,
    error: tokens.deleteToken.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { id }: ExternalProps): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: () => dispatch(actions.deleteToken(id)),
    onDone: () => dispatch(actions.listTokens())
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(APITokenDeleteActivity);
