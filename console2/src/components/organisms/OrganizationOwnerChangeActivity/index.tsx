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

import { ConcordId, ConcordKey, EntityOwner, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/orgs';
import { RequestErrorMessage } from '../../molecules';
import EntityOwnerChangeForm from '../../molecules/EntityOwnerChangeForm';

interface ExternalProps {
    orgId: ConcordId;
    orgName: ConcordKey;
    owner?: EntityOwner;
}

interface StateProps {
    changing: boolean;
    error: RequestError;
}

interface DispatchProps {
    change: (orgId: ConcordId, orgName: ConcordKey, ownerId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class OrganizationOwnerChangeActivity extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);

        this.state = { dirty: false, showConfirm: false, value: props.owner };
    }

    render() {
        const { error, owner, changing, change, orgId, orgName } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <EntityOwnerChangeForm
                    originalOwnerId={owner?.id}
                    confirmationHeader="Change organization owner?"
                    confirmationContent="Are you sure you want to change the organization's owner?"
                    onSubmit={(value) => change(orgId, orgName, value)}
                    submitting={changing}
                />
            </>
        );
    }
}

const mapStateToProps = ({ orgs }: { orgs: State }): StateProps => ({
    changing: orgs && orgs.changeOwner.running,
    error: orgs && orgs.changeOwner.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    change: (orgId, orgName, ownerId) => dispatch(actions.changeOwner(orgId, orgName, ownerId))
});
export default connect(mapStateToProps, mapDispatchToProps)(OrganizationOwnerChangeActivity);
