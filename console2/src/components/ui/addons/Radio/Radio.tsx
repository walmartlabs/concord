
import React from 'react'
import { getUnhandledProps } from '../../lib'
import Checkbox, { StrictCheckboxProps } from '../../modules/Checkbox'

export interface RadioProps extends StrictRadioProps {}

export interface StrictRadioProps extends StrictCheckboxProps {
  /** Format to show an on or off choice. */
  toggle?: boolean

  /** HTML input type, either checkbox or radio. */
  type?: 'checkbox' | 'radio'
}


/**
 * A Radio is sugar for <Checkbox radio />.
 * Useful for exclusive groups of sliders or toggles.
 * @see Checkbox
 * @see Form
 */
function Radio(props: RadioProps): React.ReactElement {
  const { toggle, type } = props

  const rest = getUnhandledProps(Radio, props)
  const radio = !toggle || undefined

  return React.createElement(Checkbox, { ...rest, type, radio, toggle })
}

Radio.handledProps = ['toggle', 'type']



Radio.defaultProps = { type: 'radio' } satisfies Partial<RadioProps>

export default Radio
