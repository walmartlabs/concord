import * as React from 'react';
import { Message } from 'semantic-ui-react';
import { RequestError } from '../../../api/common';

interface Props {
    error: RequestError;
}

export default class extends React.PureComponent<Props> {
    render() {
        const { error } = this.props;

        if (!error) {
            return <p>No error</p>;
        }

        const message = error.message ? error.message : `Server response: ${error.status}`;
        const details = error.details && error.details.length > 0 ? error.details : undefined;

        return (
            <Message negative={true}>
                <Message.Header>{message}</Message.Header>
                {details && <p>{details}</p>}
            </Message>
        );
    }
}
