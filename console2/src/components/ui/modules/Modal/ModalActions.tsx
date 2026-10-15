import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface ModalActionsProps
  extends StrictModalActionsProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictModalActionsProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string
}

/**
 * A modal can contain a row of actions.
 */
function ModalActions(props: ModalActionsProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('actions', className)

  const rest = getUnhandledProps(ModalActions, props)
  const ElementType = getElementType(ModalActions, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

ModalActions.handledProps = ['as', 'children', 'className']



export default ModalActions
