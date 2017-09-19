import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Container, Button, Form, Message } from 'semantic-ui-react';
import { createNewKeyPair } from './actions';

const CreateNewKeyPair = () => {

    const handler = () => console.log("Just a secret button press");

    return (
        <Container text>
            <h2>Create New Key Pair</h2>
            <Form success>
                <Form.Input label='Secret Name' placeholder='Concord ID' />
                <Message
                    success
                    header='Created Secret'
                    content="Your public key is"
                />
                <Button onClick={handler}>Generate New Keypair</Button>
            </Form>
        </Container>
    )
}

export {
    CreateNewKeyPair
}