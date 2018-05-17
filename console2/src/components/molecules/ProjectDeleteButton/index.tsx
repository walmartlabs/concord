import * as React from 'react';
import { Button, Confirm } from 'semantic-ui-react';

interface State {
    showConfirm: boolean;
}

interface Props {
    submitting: boolean;
    onConfirm: () => void;
}

// TODO can be refactored as a generic "button-with-confirmation" component
class ProjectDeleteForm extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { showConfirm: false };
    }

    handleShowConfirm(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        this.setState({ showConfirm: true });
    }

    handleCancel() {
        this.setState({ showConfirm: false });
    }

    handleConfirm() {
        this.props.onConfirm();
    }

    render() {
        const { submitting } = this.props;

        return (
            <>
                <Button
                    primary={true}
                    negative={true}
                    content="Delete"
                    loading={submitting}
                    onClick={(ev) => this.handleShowConfirm(ev)}
                />

                <Confirm
                    open={this.state.showConfirm}
                    header="Delete the project?"
                    content="Are you sure you want to delete the project?"
                    onConfirm={() => this.handleConfirm()}
                    onCancel={() => this.handleCancel()}
                />
            </>
        );
    }
}

export default ProjectDeleteForm;
