import * as copyToClipboard from 'copy-to-clipboard';
import * as React from 'react';
import { Button, Loader, Popup, TextArea } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { getPublicKey as apiGetPublicKey } from '../../../api/org/secret';
import { RequestErrorMessage } from '../../molecules';

import './styles.css';

interface Props {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

interface State {
    open: boolean;
    loading: boolean;
    publicKey?: string;
    error: RequestError;
}

class PublicKeyPopup extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { open: false, loading: false, error: null };
    }

    handleToggle() {
        if (this.state.open) {
            this.setState({ open: false, loading: false });
            return;
        }

        const { orgName, secretName } = this.props;

        this.setState({ open: true, loading: true });

        // TODO replace with redux
        apiGetPublicKey(orgName, secretName)
            .then((r) => {
                this.setState({
                    loading: false,
                    error: null,
                    publicKey: r.publicKey
                });
            })
            .catch((e) => {
                this.setState({
                    loading: false,
                    error: e,
                    publicKey: undefined
                });
            });
    }

    renderContent() {
        const { error, publicKey } = this.state;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (!publicKey) {
            return;
        }

        return (
            <>
                <Button
                    size="mini"
                    icon="copy"
                    content="Copy"
                    onClick={() => copyToClipboard(publicKey)}
                />
                <TextArea className="publicKeyData" value={publicKey} />
            </>
        );
    }

    render() {
        return (
            <Popup
                className="publicKeyPopup"
                hideOnScroll={true}
                open={this.state.open}
                trigger={
                    <Button
                        icon="unlock"
                        content="Public Key"
                        onClick={() => this.handleToggle()}
                    />
                }>
                <Popup.Header>Public Key</Popup.Header>
                <Popup.Content>
                    <Loader active={this.state.loading} />
                    {this.renderContent()}
                </Popup.Content>
            </Popup>
        );
    }
}

export default PublicKeyPopup;
