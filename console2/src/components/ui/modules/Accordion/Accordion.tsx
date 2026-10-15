import cx from 'clsx'

import React from 'react'
import { SemanticShorthandCollection } from '../../generic'
import { getUnhandledProps } from '../../lib'
import AccordionAccordion from './AccordionAccordion'
import { AccordionPanelProps } from './AccordionPanel'

export interface AccordionProps
  extends StrictAccordionProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictAccordionProps> {}

export interface StrictAccordionProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Additional classes. */
  className?: string

  /** Shorthand array of panels. */
  panels?: SemanticShorthandCollection<AccordionPanelProps>
}

/**
 * An accordion allows users to toggle the display of sections of content.
 */
function Accordion(props: AccordionProps): React.ReactElement {
  const { className } = props

  const classes = cx('ui', className)
  const rest = getUnhandledProps(Accordion, props)

  return React.createElement(AccordionAccordion, { ...rest, className: classes })
}

Accordion.handledProps = ['className']



export default Accordion
