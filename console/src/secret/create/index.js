import React, { Component } from "react";
import {connect} from "react-redux";
import { CreateNewKeyPair, CreatePlainSecret, CreateUserCredentials, UploadExistingKeys } from "../components";
import { actions, sagas, reducers } from "./create";
import { Container, Segment, SegmentGroup, Message } from "semantic-ui-react";


export class SecretCreate extends Component {

    constructor( props ) {
        super( props );

        this.state = {
            success: false,
            error: false
        };
    }

    render() {

        const { success, error, message, publicKey, isLoading,
                Submit_NewKeyPair, 
                Submit_PlainSecret, 
                Submit_UserCredentials, 
                Submit_ExistingKeyPair,
        } = this.props;

        return (
            <Container > 
                <SegmentGroup>
                    <Segment loading={ isLoading }>
                        <CreateNewKeyPair onSubmit={ Submit_NewKeyPair } />
                        
                        <CreatePlainSecret onSubmit={ Submit_PlainSecret } />

                        <CreateUserCredentials onSubmit={ Submit_UserCredentials } />

                        <UploadExistingKeys onSubmit={ Submit_ExistingKeyPair } />

                        <Message success 
                            hidden={ !success }
                            header={ message }
                            content={ publicKey } />

                        <Message error 
                            hidden={ !error }
                            header={ message } />
                    </Segment>
                </SegmentGroup>
            </Container> 
        )
    }
}

const mapStateToProps = ( state ) => {
    return {
        success: state.secretForm.success,
        error: state.secretForm.error,
        publicKey: state.secretForm.publicKey,
        message: state.secretForm.message,
        isLoading: state.secretForm.isLoading
    };
}

const mapDispatchToProps = (dispatch) => ({
    Submit_NewKeyPair: ( values ) => dispatch( actions.CreateNewKeyPair( values ) ),
    Submit_PlainSecret: ( values ) => dispatch( actions.CreatePlainSecret( values ) ),
    Submit_UserCredentials: ( values ) => dispatch( actions.CreateWithUserCredentials( values ) ),
    Submit_ExistingKeyPair: ( values ) => dispatch( actions.CreateWithExistingKeys( values ) )
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretCreate);

export { reducers, sagas }