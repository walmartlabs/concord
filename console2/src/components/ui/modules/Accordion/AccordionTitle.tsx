import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import React from 'react'
import Icon, { IconProps } from '../../elements/Icon'
import { SemanticShorthandContent, SemanticShorthandItem } from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'

export interface AccordionTitleProps
  extends StrictAccordionTitleProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictAccordionTitleProps> {}

export interface StrictAccordionTitleProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Whether or not the title is in the open state. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  /** Shorthand for Icon. */
  icon?: SemanticShorthandItem<IconProps>

  /** AccordionTitle index inside Accordion. */
  index?: number | string

  /**
   * Called on click.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClick?: (event: React.MouseEvent<HTMLDivElement>, data: AccordionTitleProps) => void
}

/**
 * A title sub-component for Accordion component.
 */
function AccordionTitle(props: AccordionTitleProps): React.ReactElement {
  const { active, children, className, content, icon } = props

  const classes = cx(useKeyOnly(active, 'active'), 'title', className)
  const rest = getUnhandledProps(AccordionTitle, props)
  const ElementType = getElementType(AccordionTitle, props)
  const iconValue = _isNil(icon) ? 'dropdown' : icon

  const handleClick = (e: React.MouseEvent<HTMLDivElement>): void => {
    _invoke(props, 'onClick', e, props)
  }

  if (!childrenUtils.isNil(children)) {
    return React.createElement(
      ElementType,
      { ...rest, className: classes, onClick: handleClick },
      children,
    )
  }

  return React.createElement(
    ElementType,
    { ...rest, className: classes, onClick: handleClick },
    Icon.create(iconValue, { autoGenerateKey: false }),
    content,
  )
}

AccordionTitle.handledProps = [
  'active',
  'as',
  'children',
  'className',
  'content',
  'icon',
  'index',
  'onClick',
]



AccordionTitle.create = createShorthandFactory(AccordionTitle, (content: React.ReactNode) => ({
  content,
}))

export default AccordionTitle
