import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import {
    Card,
    CardContent,
    Dimmer,
    Form,
    Image,
    InputOnChangeData,
    Loader,
    Message
} from 'semantic-ui-react';
import { State as AppState } from '../../../reducers';

import { actions, selectors } from './effects';

export { actions, selectors, sagas, reducers, State } from './effects';

interface LoginData {
    username: string;
    password: string;
}

interface LoginStateProps {
    loggingIn: boolean;
    apiError: string | null;
}

interface LoginDispatchProps {
    onSubmit: (data: LoginData) => void;
}

class Login extends React.Component<LoginStateProps & LoginDispatchProps, LoginData> {
    constructor(props: LoginStateProps & LoginDispatchProps) {
        super(props);
        this.state = { username: '', password: '' };
    }

    handleChange(e: {}, { name, value }: InputOnChangeData) {
        this.setState({ [name]: value });
    }

    handleSubmit() {
        this.props.onSubmit(this.state);
    }

    render() {
        const { username, password } = this.state;

        return (
            <Card centered={true}>
                <CardContent>
                    <Image id="concord-logo" src="/images/concord.svg" size="medium" />

                    <Dimmer active={this.props.loggingIn} inverted={true}>
                        <Loader />
                    </Dimmer>

                    <Form error={!!this.props.apiError} onSubmit={() => this.handleSubmit()}>
                        <Form.Input
                            name="username"
                            label="Username"
                            icon="user"
                            required={true}
                            value={username}
                            onChange={(e, data) => this.handleChange(e, data)}
                        />
                        <Form.Input
                            name="password"
                            label="Password"
                            type="password"
                            icon="lock"
                            required={true}
                            value={password}
                            autoComplete="current-password"
                            onChange={(e, data) => this.handleChange(e, data)}
                        />

                        <Message error={true} content={this.props.apiError} />
                        <Form.Button primary={true} fluid={true}>
                            Login
                        </Form.Button>
                    </Form>
                </CardContent>
            </Card>
        );
    }
}

const mapStateToProps = ({ login }: AppState): LoginStateProps => ({
    loggingIn: selectors.isLoggingIn(login),
    apiError: selectors.getError(login)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): LoginDispatchProps => ({
    onSubmit: ({ username, password }: LoginData) => dispatch(actions.doLogin(username, password))
});

export default connect(mapStateToProps, mapDispatchToProps)(Login);
