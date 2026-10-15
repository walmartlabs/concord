import _has from 'lodash-es/has'
import _invoke from 'lodash-es/invoke'

import React from 'react'
import { SemanticShorthandItem } from '../../generic'
import { getUnhandledProps } from '../../lib'
import Button, { ButtonProps } from '../../elements/Button'
import Modal, { ModalProps } from '../../modules/Modal'
import { ModalContentProps } from '../../modules/Modal/ModalContent'
import { ModalHeaderProps } from '../../modules/Modal/ModalHeader'

export interface ConfirmProps extends StrictConfirmProps {}

export interface StrictConfirmProps {
  /** The ModalContent text. */
  content?: SemanticShorthandItem<ModalContentProps>

  /** The ModalHeader text. */
  header?: SemanticShorthandItem<ModalHeaderProps>

  /**
   * Called when the Modal is closed without clicking confirm.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onCancel?: (event: React.MouseEvent<HTMLAnchorElement>, data: ConfirmProps) => void

  /**
   * Called when the OK button is clicked.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onConfirm?: (event: React.MouseEvent<HTMLAnchorElement>, data: ConfirmProps) => void

  /** Whether or not the modal is visible. */
  open?: boolean
}

/**
 * A Confirm modal gives the user a choice to confirm or cancel an action/
 * @see Modal
 */
function Confirm(props: ConfirmProps): React.ReactElement {
  const { content, header, open } = props

  const handleCancel = (e: React.MouseEvent<HTMLElement>): void => {
    _invoke(props, 'onCancel', e, props)
  }

  const handleCancelOverrides = (predefinedProps: Partial<ButtonProps>) => ({
    onClick: (e: React.MouseEvent<HTMLButtonElement>, buttonProps: ButtonProps): void => {
      _invoke(predefinedProps, 'onClick', e, buttonProps)

      handleCancel(e)
    },
  })

  const handleConfirmOverrides = (predefinedProps: Partial<ButtonProps>) => ({
    onClick: (e: React.MouseEvent<HTMLButtonElement>, buttonProps: ButtonProps): void => {
      _invoke(predefinedProps, 'onClick', e, buttonProps)

      _invoke(props, 'onConfirm', e, props)
    },
  })

  // `Confirm` forwards every unhandled prop to `Modal`, while its public contract only
  // declares the Confirm-specific surface, so the forwarded props are widened here.
  const rest = getUnhandledProps(Confirm, props) as unknown as Partial<ModalProps>

  const openProp: { open?: boolean } = {}
  if (_has(props, 'open')) openProp.open = open

  return React.createElement(
    Modal,
    { ...rest, ...openProp, size: 'small', onClose: handleCancel },
    Modal.Header.create(header, { autoGenerateKey: false }),
    Modal.Content.create(content, { autoGenerateKey: false }),
    React.createElement(
      Modal.Actions,
      null,
      Button.create('Cancel', {
        autoGenerateKey: false,
        overrideProps: handleCancelOverrides,
      }),
      Button.create('OK', {
        autoGenerateKey: false,
        defaultProps: { primary: true },
        overrideProps: handleConfirmOverrides,
      }),
    ),
  )
}

Confirm.handledProps = ['content', 'header', 'onCancel', 'onConfirm', 'open']



Confirm.defaultProps = { content: 'Are you sure?' } satisfies Partial<ConfirmProps>

export default Confirm
