import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { DropdownItemProps } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { actions, State } from '../../../state/data/secrets';
import { Secrets } from '../../../state/data/secrets/types';
import { comparators } from '../../../utils';
import { FormikDropdown } from '../../atoms';

interface ExternalProps {
    orgName: ConcordKey;
    name: string;
    label?: string;
    required?: boolean;
    fluid?: boolean;
}

interface StateProps {
    loading: boolean;
    options: DropdownItemProps[];
}

interface DispatchProps {
    load: () => void;
}

class SecretDropdown extends React.PureComponent<ExternalProps & StateProps & DispatchProps> {
    componentDidMount() {
        this.props.load();
    }

    render() {
        const { orgName, load, ...rest } = this.props;

        return <FormikDropdown selection={true} {...rest} />;
    }
}

const makeOptions = (data: Secrets): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName)
        .map(({ name }) => ({
            value: name,
            text: name
        }));
};

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    loading: secrets.listSecrets.running,
    options: makeOptions(secrets.secretById)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { orgName }: ExternalProps): DispatchProps => ({
    load: () => dispatch(actions.listSecrets(orgName))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretDropdown);
